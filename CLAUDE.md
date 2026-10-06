# boiler-service

Drives the three devices of the boiler room — the furnace, the hot water pump and the heating
pump — as relays of one Shelly Pro 4. It does not decide whether the house needs heat: once a
minute it asks `water-service` and `heating-service`, and turns their two answers into relay
states.

Part of the smart-home-automation-system organization — org-wide conventions, the
repository map and working rules come from the workspace-level context
(`organization-repository/claude/organization.md`). The user writes the code in this
repository themselves; Claude's default role here is analysis, code review and security
review.

## Role in the system

- Calls: `water-service` (`GET /home/water/status/active`) and `heating-service`
  (`GET /home/heating/status/active`) directly over the cluster network, and the Shelly Pro 4
  on the LAN over HTTP.
- Is called by: anything outside the cluster through `api-gateway-service`
  (`/home/boiler/**`); no service inside the cluster calls it.
- Publishes to: the headers exchange `notification` on the `/notification` virtual host of
  RabbitMQ (HAS-109) — an alert when the Shelly stops answering, read by
  `notification-service`. It consumes nothing.
- No database. The `cholewa-commons` R2DBC auto-configuration stays inactive — there is no
  R2DBC on the classpath and no `database.host`; keep it that way.
- Uses libraries: `cholewa-commons` (error handling), `smart-home-sdk` (`SystemActiveReply`
  only), `shelly-client` (`Relay`, `ShellyPro4StatusResponse`).
- One endpoint: `GET /home/boiler/status`, a read of the in-memory state.

## The control pass — what must not be broken

`StatusCron.updateStatus` runs with `fixedDelay` one minute (10 s initial delay), so a slow
pass postpones the next one instead of overlapping with it. A pass queries both services, then
`BoilerService.controlBoilerDevices` drives the devices **in a fixed order: hot water pump,
heating pump, furnace**.

- **Every step has its own `onErrorResume`** (`skip(...)`). The pass used to be a fail-fast
  `then()` chain: one slow relay reply cancelled the remaining devices, including the furnace,
  which is controlled last and then kept burning until the next successful pass (HAS-128).
  A new step in the chain gets its own `onErrorResume`, never a shared one at the end.
- **Skipping a step is safe only because device state is written exclusively from a device
  response.** `BoilerConfig` (in-memory, one `DeviceStatus` per device) is updated in the
  `doOnNext` of a status read or of a relay command, never from what the service intended. A
  pump that did not confirm it runs is therefore never the reason the furnace fires. Do not
  write state optimistically before the device answered.
- The furnace follows the cached pump states: on when at least one pump works, off when both
  are off. The hot water pump has priority — while it works the heating pump is switched off.
- Device state is re-read from the Shelly only when the cached entry is older than a minute
  (`lastMessage` timestamp). A skipped step leaves the timestamp stale, so the next pass asks
  the device again.
- **An unreachable neighbour means "not active".** `HeatingClient` and `WaterClient` answer
  `active=false` on any error, so a failing `water-service` or `heating-service` makes the
  boiler idle, not stuck on. `ShellyClient` does the opposite: every failure becomes a
  `BoilerException`, which the pass skips.
- All three HTTP clients have a 5 s `responseTimeout` and a 10 s connect timeout
  (`AppConfig`). When changing a timeout, check what it now aborts — that is how the fail-fast
  chain above became a problem.
- The state lives in the memory of one instance. After a restart every device reads as "not
  working" until the first pass has asked the Shelly, and a second replica would drive the
  same relays from its own copy of the state — do not scale the Deployment.
- The reactive `@Scheduled` method is invoked once and resubscribed for every run. Nothing in
  `updateStatus` is computed while the `Mono` is built — keep time and state reads inside
  operator lambdas or `Mono.defer`, as the services do.

## The Shelly monitor — telling the household the device is gone (HAS-109)

A Shelly that stops answering used to show only in the log, as three skipped steps a minute,
while the furnace kept the state it was left in. `ShellyAvailabilityMonitor` turns that into
notifications: an **alert** (`error`) once its calls have been failing for
`boiler.shelly-monitor.offline-after` (5 min), a **reminder** (`warn`) every
`reminder-interval` (1 h) while it lasts, one **info** when the device answers again. The
numbers are the owner's (2026-10-06): five minutes lets a restart or a firmware update pass
unnoticed. "Urgent" means the red alert on Discord — an SMS waits for the SMS library
(HAS-68) and will be a change in `notification-service`, not here.

- **`ShellyClient` is the only source of truth.** All six calls go through `watched(...)`,
  which tells a `ShellyCallListener` — the monitor — how the call ended and which kind it was
  (`ShellyCall.STATUS` or `COMMAND`). A failure is
  everything that leaves the device undriven: no connection, a timeout, a 4xx or 5xx, a 200
  that does not decode, a 200 without a body, and a 200 with JSON that is not the model
  (`{}` decodes into an object of nulls and used to read as "the relay is off" — each call
  now checks the one field it is made for: `output` of a status, `ison` of a command. The
  real device was seen sending both: `output` in the production log, `ison` in a read of
  `/relay/0`, the endpoint the commands use). A new call to the Shelly goes through
  `watched(...)` as well. The listener is an interface in the `client` package so that the
  client does not depend on the services that use it.
- **The device is judged by whole passes, not by single calls.** A pass in which any call
  failed is a **failed** pass. A pass without a failure **proves** the device works when it
  answered every kind of call that had been failing — or, failing that, when it answered and
  nothing has failed for `offline-after`: a command is sent only when a relay has to change,
  and without this second way out an outage of the commands would stay open for days and the
  next real one would arrive as a yellow reminder dated from the old start. Every other pass — one that did not
  call the device, or made only status reads while it is the commands that fail — says
  nothing. Judged call by call, two devices that are not driven would never be reported: one
  whose status reads answer while every command is refused (a firmware update changing one
  path), and one that gets a call through now and then — and after an alert, every stray
  answer would send a green "works again" followed by a new red alert five minutes later.
- **The outage is counted from the first failed call after the device was last proven to
  work.** An alert or a reminder goes out only at the end of a failed pass, and a reported
  outage ends with the first pass that proves the device. Time alone proves nothing: a
  failure that nothing followed for as long as `offline-after` is forgotten, so a second one
  hours later does not read as "failing since this morning".
- **An outage whose alert never got through is announced afterwards.** The device and the
  broker tend to go down together (power, network). If the alert was due and the device comes
  back before the broker, the info "works again, its calls failed from … to …" still goes out
  once the broker takes it.
- **The texts say "failing calls", not "offline"**, and that the furnace and the pumps "may
  not be controlled": a device answering 401 to its commands is not a power cut, and the
  alert should not send the owner looking for one.
- **`StatusCron` asks the monitor at the end of every pass**, after the devices were driven —
  also when the pass itself ended with an error, which `StatusCron` logs and swallows first.
  `report()` never signals an error and is cut off after 30 s (`PUBLISH_TIMEOUT`): a broker
  that is down or hangs must neither fail nor stall the control of the furnace. It is
  deferred, because the reactive `@Scheduled` method is invoked once and resubscribed;
  `ShellyAvailabilityMonitorTest` subscribes twice to the same `Mono` for that.
- **The state moves on only when the broker has taken the message** (correlated confirm and no
  return, `NotificationPublisher`). A failed publish is logged at ERROR and tried again with
  the next pass, as the same kind of message — so a broker outage delays an alert by a
  minute at a time, it does not turn it into a reminder an hour later. The other side of
  that, accepted: a broker so slow that it confirms after the 10 s limit has taken the
  message, and the next pass sends it again — duplicates for as long as the broker is that
  slow. And a broker that hangs holds the end of the pass for up to 30 s; with `fixedDelay`
  the next pass starts that much later. A pending alert or reminder is tried again at the end
  of the next failed pass — during an outage that is every pass; the pending news of a
  return at the end of every pass.
- **The state is in memory.** After a restart during an outage the new pod counts from its own
  first failed call and sends the alert again five minutes later; if the device returns
  before that, no info is sent, because this pod never reported it gone. A duplicate, never a
  silence — accepted instead of a database.
- **One connection, auto-configured.** Publishing is all this service does on the broker, so
  `spring.rabbitmq.*` points straight at the `/notification` virtual host and the
  auto-configured `RabbitTemplate` is used — unlike `heating-service`, which needs a second,
  hand-built connection. Do not declare a `ConnectionFactory` or `RabbitTemplate` bean:
  `RabbitAutoConfiguration` backs off, and `mandatory`, `observation-enabled`, the confirms and
  the returns set in `application.yaml` are silently lost. `BoilerServiceApplicationTest` pins
  them. Should the service ever consume from another virtual host, that takes the
  `heating-service` shape (`NotificationRabbitConfig` there).
- The broker password (`rabbitmq-password`, the key `notification-password` of the secret
  `rabbitmq`) has **no default** outside the `test` document: with one the pod would start,
  turn Ready and fail every publish. The connection is opened by the first publish, so a wrong
  password shows only with the first alert — a green rollout proves nothing about it. The
  smoke test after a deploy is `GET /actuator/health` on the management port through a
  port-forward: the RabbitMQ health indicator opens the connection, so `UP` means the broker
  took the password and the connection shows on the broker under the pod's name. That proves
  the connection, not the route: **nothing in the service can force a publish** (an endpoint
  for it would be reachable through the gateway), so the exchange and the headers are proven
  by running the jar outside the cluster with the Shelly pointed at a dead address and a short
  `offline-after`, against the real broker with `env: dev`, and reading the message from
  `notification.dev.alert` — done before the first release (HAS-109, with the admin user of the
  broker). The probes do
  not do this — the `readiness` and `liveness` groups leave the broker out, on purpose: a
  broker that is down must not restart the service that drives the furnace.
- The connection is named after the pod (`RabbitConfig`, the org convention of HAS-106).
- `ShellyMonitorProperties` has a unit and both bounds: a bare number is minutes, `offline-after`
  2 min – 1 h, `reminder-interval` 5 min – 24 h.

## Errors

`BoilerExceptionProcessor` answers a `BoilerException` with 500 and logs it at ERROR in the
`cholewa-commons` format (`Handled [BoilerException]: …`). Its `details` is the exception
message, which is always one of the fixed texts in `ShellyClient` — the actual device failure
is logged there, with the stack trace, and never reaches a response. Keep it that way: do not
put the cause's message into a `BoilerException`.

Today no `BoilerException` reaches the HTTP layer at all: the only endpoint reads the cached
state, and the control pass swallows the exception in `skip(...)`. The processor matters from
the first endpoint that calls the Shelly on request.

## Tests

- Surefire activates the `test` profile for every
  class (`systemPropertyVariables` in the pom), and the `test` document of `application.yaml`
  switches the console back to plain text and logbook to the `http` style. Without it the
  `@SpringBootTest` context installs the logstash encoder for every test that follows in the
  same JVM.
- **A test context must never run the control pass** — on the home network it would switch the
  real relays ten seconds after the context started. Two guards, both tied to the `test`
  profile: `SchedulingConfig` (`@EnableScheduling`, `@Profile("!test")`) schedules nothing, and
  the `test` document points the Shelly at `localhost:1`. Surefire activates the profile only
  under Maven, so **every `@SpringBootTest` also carries `@ActiveProfiles("test")`** — started
  from an IDE without it, the class comes up with `home` and the real address.
  `BoilerServiceApplicationTest` pins that nothing is scheduled. Nothing in a test connects
  to the broker either: no listener exists, and a publisher connects with its first send.
- The client tests use `com.squareup.okhttp3:mockwebserver3` (`mockwebserver3.*`,
  `MockResponse.Builder`, `close()`). Do not go back to the legacy `mockwebserver` artifact —
  it puts JUnit 4 on the classpath, where a JUnit 4 test compiles and never runs.
- `BoilerServiceTest` pins the fail-safe pass: the furnace is still controlled when either
  pump fails. `BoilerServiceApplicationTest` is the only proof that the whole context starts
  with the libraries as they are — it is what a library bump has to pass.
- Surefire includes only `**/*Test.java` and `**/*IT.java`; a class named `...Tests` is
  silently skipped.

## Build & run

- Build: `mvn verify` (JDK 21). The enforcer runs `dependencyConvergence`, so a new
  dependency with a conflicting transitive version fails the build — pin the version in
  `dependencyManagement`, do not exclude annotations javac needs (the `apiguardian-api`
  exclusion once produced `unknown enum constant` warnings for every logbook class; since
  logbook 4.2.0 no pin is needed).
- Run locally: `mvn spring-boot:run -Dspring-boot.run.profiles=local` — application on 6007,
  Actuator on 8007, `heating-service` and `water-service` expected on `localhost:6002` and
  `localhost:6006`. **The Shelly address is the real device in every profile but `test`, so a
  local run switches the real relays** — ten seconds after the start. It also needs
  `rabbitmq-password` in the environment (any value, unless a notification is to be published). With the neighbours not running
  locally both read as "not active", and the pass turns the pumps and the furnace off, against
  the instance in the cluster, which turns them back on within a minute. Start it locally only
  with the Shelly address overridden or with that fight understood.
- In the cluster: 6200 / 8200, probes on `/actuator/health/{readiness,liveness}`, JSON logs,
  tracing without an exporter (see the org context).
- Release: `gh release create <X.Y.Z>` triggers `release.yml`, which pushes
  `magikabdul/boiler-service:<X.Y.Z>` to Docker Hub; the manifest lives in `deployment-tools`
  (`workshop/boiler-service.yaml`). Tags have no `v` prefix. Release flow: the `release` skill.
