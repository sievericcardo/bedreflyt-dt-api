package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class Task (
    val taskName: String
) : Serializable
{
    override fun toString(): String {
        return "Task(taskName='$taskName')"
    }
}