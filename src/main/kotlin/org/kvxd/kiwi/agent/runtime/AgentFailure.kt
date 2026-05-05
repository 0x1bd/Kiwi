package org.kvxd.kiwi.agent.runtime

class AgentFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
