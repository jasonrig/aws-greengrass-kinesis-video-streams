package by.jasonrig.streaming

import com.amazonaws.services.lambda.runtime.Context
import org.freedesktop.gstreamer.GstObject
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

/**
 * Implements the AWS Lambda function that controls the stream
 */
class Lambda : IoTRequest(), StreamLifecycleEvents {

    var currentStreamParameters: Map<String, String>? = null

    companion object {
        // The current stream object
        var currentStreamer: Streamer? = null

        // The AWS credentials provider used to authenticate the stream
        val credentialsProvider: AwsCredentialsProvider = GreengrassCredentialsProvider()

        // JSON keys for parsing the incoming requests
        const val TASK_KEY = "task"
        const val START_TASK = "start"
        const val STOP_TASK = "stop"
        const val VIDEO_DEVICE_KEY = "videoDevice"
        const val STREAM_NAME_KEY = "streamName"
        const val AWS_REGION = "awsRegion"
    }

    /**
     * Parses and executes the stream control request
     * @param input the incoming stream control request
     * @param context the request context
     * @return the result of the stream control request
     */
    override fun handleIoTRequest(input: Map<String, String>?, context: Context?): Response {
        require(input != null) {
            return Response("Missing input", Response.Status.ERROR)
        }

        return when (input[TASK_KEY]) {
            START_TASK -> startTask(input)
            STOP_TASK -> stopTask()
            else -> Response("No task specified", Response.Status.ERROR)
        }
    }

    /**
     * Starts the stream
     * The incoming request must contain:
     *  - videoDevice: the path to the video device, e.g. /dev/video0 (must be enabled in AWS Greengrass configuration)
     *  - streamName: the name of the AWS Kinesis Video Stream name (AWS Greengrass group role must have the correct permissions in AWS IAM)
     *  - awsRegion: the region of the AWS Kinesis Video stream
     * @param input the request input
     * @return the result of the stream start request
     */
    private fun startTask(input: Map<String, String>): Response {
        // If `currentStreamer` is not null, it means that the stream is currently running,
        // so stop the current stream before starting it again.
        if (currentStreamer != null) {
            publishMessage(stopTask())
        }

        // Set up the configuration parameters from the request object
        // An error will be returned if the required parameters are missing
        val config = try {
            Streamer.ConnectionParameters(
                videoDevice = input.getOrDefault(VIDEO_DEVICE_KEY, "/dev/video0"),
                streamName = input[STREAM_NAME_KEY] ?: error("Stream name [$STREAM_NAME_KEY] must be provided"),
                awsRegion = input[AWS_REGION] ?: error("AWS Region [$AWS_REGION] must be provided")
            )
        } catch (e: IllegalStateException) {
            return Response("Error: ${e.message}", Response.Status.ERROR)
        }

        // Create and start the streamer
        val newStreamer = Streamer(config, credentialsProvider, this)
        newStreamer.start()

        // Keep references to the current configuration and streamer object
        // `currentStreamParameters` is kept in case the stream needs to be restarted due to an error
        // `currentStreamer` is kept so we can stop the stream later
        currentStreamParameters = input
        currentStreamer = newStreamer

        // Return the result of the request (SUCCESS if we get this far)
        return Response("Stream started", Response.Status.SUCCESS)
    }

    /**
     * Stops any running stream
     */
    private fun stopTask(): Response {
        currentStreamer?.stop()
        currentStreamer = null
        return Response("Streaming is stopped", Response.Status.SUCCESS)
    }

    /**
     * Lifecycle event - executed when the stream ends
     * @see StreamLifecycleEvents.onEnd
     * @see org.freedesktop.gstreamer.Bus.EOS
     * @param source the gstreamer handling the stream
     */
    override fun onEnd(source: GstObject) {
        val msg = "[${source.name}] gstreamer stopped"
        println(msg)
        publishMessage(Response(msg, Response.Status.SUCCESS))
    }

    /**
     * Lifecycle event - executed when a stream error is encountered
     * @see StreamLifecycleEvents.onError
     * @see org.freedesktop.gstreamer.Bus.ERROR
     * @param source the gstreamer handling the stream
     * @param code error code
     * @param message error message
     */
    override fun onError(source: GstObject, code: Int, message: String) {
        val msg = "[${source.name}] $message"
        println("ERROR | $msg")
        publishMessage(Response(msg, Response.Status.ERROR, code))

        if (source.name == "kvssink0" && code == 1 && currentStreamParameters != null) {
            publishMessage(Response("Restarting stream", Response.Status.NOTICE))
            startTask(currentStreamParameters!!)
        }
    }

    /**
     * Lifecycle event - executed when a warning is emitted by the stream
     * @see StreamLifecycleEvents.onWarn
     * @see org.freedesktop.gstreamer.Bus.WARNING
     * @param source the gstreamer handling the stream
     * @param code warning code
     * @param message warning message
     */
    override fun onWarn(source: GstObject, code: Int, message: String) {
        val msg = "[${source.name}] $message"
        println("WARNING | $msg")
        publishMessage(Response(msg, Response.Status.WARNING, code))
    }

}