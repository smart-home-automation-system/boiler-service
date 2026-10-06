package cloud.cholewa.boiler.client;

/**
 * The two kinds of call the service makes to the Shelly. They can fail apart - a device may answer
 * its status and refuse every command - so an answer to one says nothing about the other.
 */
public enum ShellyCall {
    STATUS,
    COMMAND
}
