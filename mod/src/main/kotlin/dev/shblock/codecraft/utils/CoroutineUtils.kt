package dev.shblock.codecraft.utils

import kotlinx.coroutines.Job

@Suppress("FunctionName")
fun CompletedJob(): Job {
    val job = Job()
    job.complete()
    return job
}
