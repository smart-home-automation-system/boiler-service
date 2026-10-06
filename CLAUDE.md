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
- No database and no RabbitMQ. The `cholewa-commons` R2DBC auto-configuration stays inactive —
  there is no R2DBC on the classpath and no `database.host`; keep it that way.
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

- No test class carries `@ActiveProfiles`; surefire activates the `test` profile for every
  class (`systemPropertyVariables` in the pom), and the `test` document of `application.yaml`
  switches the console back to plain text and logbook to the `http` style. Without it the
  `@SpringBootTest` context installs the logstash encoder for every test that follows in the
  same JVM.
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
  `localhost:6006`. **The Shelly address is the real device in every profile, so a local run
  switches the real relays** — ten seconds after the start. With the neighbours not running
  locally both read as "not active", and the pass turns the pumps and the furnace off, against
  the instance in the cluster, which turns them back on within a minute. Start it locally only
  with the Shelly address overridden or with that fight understood.
- In the cluster: 6200 / 8200, probes on `/actuator/health/{readiness,liveness}`, JSON logs,
  tracing without an exporter (see the org context).
- Release: `gh release create <X.Y.Z>` triggers `release.yml`, which pushes
  `magikabdul/boiler-service:<X.Y.Z>` to Docker Hub; the manifest lives in `deployment-tools`
  (`workshop/boiler-service.yaml`). Tags have no `v` prefix. Release flow: the `release` skill.
