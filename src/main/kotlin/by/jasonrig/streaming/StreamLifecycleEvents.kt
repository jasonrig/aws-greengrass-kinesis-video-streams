package by.jasonrig.streaming

import org.freedesktop.gstreamer.GstObject

/**
 * A set of events that the streamer can emit
 */
interface StreamLifecycleEvents {
    /**
     * Called on end of stream
     * @see org.freedesktop.gstreamer.Bus.EOS
     * @param source the gstreamer handling the stream
     */
    fun onEnd(source: GstObject)

    /**
     * Called on stream error
     * @see org.freedesktop.gstreamer.Bus.ERROR
     * @param source the gstreamer handling the stream
     * @param code error code
     * @param message error message
     */
    fun onError(source: GstObject, code: Int, message: String)

    /**
     * Called when a warning is emitted
     * @see org.freedesktop.gstreamer.Bus.WARNING
     * @param source the gstreamer handling the stream
     * @param code warning code
     * @param message warning message
     */
    fun onWarn(source: GstObject, code: Int, message: String)

    /**
     * Called we the credentials used for the stream are due to expire
     */
    fun onCredentialsExpiration()

}