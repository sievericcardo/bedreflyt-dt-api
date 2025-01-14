package no.uio.bedreflyt.api.model.triplestore

class TaskDependency (
    val diagnosis: String,
    val treatment: String,
    val task: String,
    val dependsOn: String
)