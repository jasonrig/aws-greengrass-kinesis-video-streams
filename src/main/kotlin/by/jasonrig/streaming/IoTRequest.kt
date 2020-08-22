package by.jasonrig.streaming

import com.amazonaws.greengrass.javasdk.IotDataClient
import com.amazonaws.greengrass.javasdk.model.PublishRequest
import com.amazonaws.greengrass.javasdk.model.QueueFullPolicy
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.google.gson.GsonBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
        private val iotThingName = System.getenv("AWS_IOT_THING_NAME")
        private val iotDataClient = IotDataClient()
        private val gson = GsonBuilder().create()
        private val logger: Logger = LoggerFactory.getLogger(IoTRequest::class.java)
    }

    /**
     * Publishes a message to MQTT
     */
    protected fun publishMessage(message: Response) {
        val messagePayload = message.toString()
        val messageRequest = PublishRequest()
            .withTopic(mqttTopic)
            .withPayload(ByteBuffer.wrap(messagePayload.toByteArray(Charsets.UTF_8)))
            .withQueueFullPolicy(queuePolicy)
        iotDataClient.publish(messageRequest)
        logger.info("Published message: $messagePayload")
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