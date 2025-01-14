package no.uio.bedreflyt.api.model.triplestore

class TaskDependency (
    val treatment: String,
    val diagnosis: String,
    val task: String,
    val dependsOn: String
)