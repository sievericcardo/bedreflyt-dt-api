package no.uio.bedreflyt.api.model.triplestore

import java.io.Serializable

data class MonitoringCategory (
    val category: Int,
    val description: String
) : Serializable
{
    override fun toString(): String {
        return "MonitoringCategory(category=$category, description='$description')"
    }
}