package by.jasonrig.streaming

import com.amazonaws.greengrass.javasdk.IotDataClient
import com.amazonaws.greengrass.javasdk.model.PublishRequest
import com.amazonaws.greengrass.javasdk.model.QueueFullPolicy
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import java.nio.ByteBuffer

/**
 * Extends the RequestHandler and provdes a "handleIoTRequest", which publishes the result
 * to the specified MQTT topic
 * @param queuePolicy the MQTT policy
 * @param mqttTopic the MQTT topic to which the function result should be published
 */
abstract class IoTRequest(
    private val queuePolicy: QueueFullPolicy = QueueFullPolicy.AllOrException,
    private val mqttTopic: String = System.getenv("MQTT_TOPIC")
) : RequestHandler<Map<String, String>, String> {

    companion object {
        private val iotDataClient = IotDataClient()
    }

    /**
     * Publishes a message to MQTT
     */
    protected fun publishMessage(message: Response) {
        val messageRequest = PublishRequest()
            .withTopic(mqttTopic)
            .withPayload(ByteBuffer.wrap(message.toString().toByteArray(Charsets.UTF_8)))
            .withQueueFullPolicy(queuePolicy)
        iotDataClient.publish(messageRequest)
    }

    /**
     * Calls `handleIoTRequest` and publishes the result
     * @param input the payload of the AWS Lambda request
     * @param context the request context
     * @return the result of the AWS Lambda function execution
     */
    override fun handleRequest(input: Map<String, String>?, context: Context?): String {
        require(mqttTopic.isNotEmpty()) {
            "MQTT_TOPIC environment variable is blank, so cannot return publish the AWS Lambda result"
        }
        val result = handleIoTRequest(input, context)
        publishMessage(result)
        return result.toString()
    }

    /**
     * Method called when AWS Lambda function is triggered
     * @see handleRequest
     * @param input the payload of the AWS Lambda request
     * @param context the request context
     * @return the result of the AWS Lambda function execution
     */
    abstract fun handleIoTRequest(input: Map<String, String>?, context: Context?): Response

}