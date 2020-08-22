package by.jasonrig.streaming

import com.google.gson.GsonBuilder

/**
 * Provides a structured object to deliver status messages
 * @param message the status message
 * @param status a flag to indicate the severity of the message
 * @param code an optional status code
 */
class Response(message: String, status: Status, code: Int? = null, extra: Map<String, Any>? = null) {
    private val message = HashMap<String, Any>()

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()
    }

    init {
        this.message["message"] = message
        this.message["status"] = status.text
        code?.also {
            this.message["code"] = it
        }
        extra?.also {
            this.message["extra"] = extra
        }
    }

    /**
     * Encodes the response data as a JSON string
     * @return a JSON string
     */
    override fun toString(): String {
        return gson.toJson(message)
    }

    /**
     * The response severity
     * @param text the string representation of the severity
     */
    enum class Status(val text: String) {
        SUCCESS("SUCCESS"),
        ERROR("ERROR"),
        WARNING("WARNING"),
        NOTICE("NOTICE")
    }
}