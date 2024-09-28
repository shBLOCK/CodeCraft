package dev.shblock.codecraft.core.connect

import dev.shblock.codecraft.core.cmd.CmdContext
import dev.shblock.codecraft.core.msg.Msg
import dev.shblock.codecraft.utils.UserSourcedException
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.slf4j.Logger

class CCClientCmdContext(val client: CCClient) : CmdContext() {
    @Suppress("OVERRIDE_BY_INLINE")
    override inline val mc get() = client.mc

    override val scope: CoroutineScope
        get() = client + SupervisorJob(client.coroutineContext[Job])

    @Suppress("OVERRIDE_BY_INLINE")
    override inline val logger: Logger
        get() = client.logger

    override val active: Boolean
        get() = client.lifecycle == CCClient.Lifecycle.ACTIVE && scope.isActive

    override suspend fun handleCmdException(exception: Exception) {
        if (exception is UserSourcedException) {
            logger.warn("$this had an UserSourcedException", exception)
        } else {
            logger.error("Unexpected error in $this", exception)
        }

        withContext(NonCancellable) {
            client.launch(Dispatchers.Unconfined) {
                client.close(
                    if (exception is UserSourcedException) CloseReason.Codes.VIOLATED_POLICY else CloseReason.Codes.INTERNAL_ERROR,
                    exception.toString()
                )
            }
        }
    }

    override suspend fun handleMsg(msg: Msg) = client.sendMsg(msg)
}