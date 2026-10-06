package cloud.cholewa.boiler.client;

/**
 * Hears how every call to the Shelly ended. It lives next to the client, so the client does not
 * have to know who listens or what is done about a device that keeps failing.
 */
public interface ShellyCallListener {

    /** The device answered a call, whatever the call was. */
    void recordAnswer();

    /**
     * A call failed: no connection, no answer in time, an error status, or an answer that is not
     * one - an empty body, something that does not decode.
     */
    void recordFailure();
}
