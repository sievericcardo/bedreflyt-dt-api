package no.uio.bedreflyt.api.config

object DatabaseContextHolder {
    private val contextHolder = ThreadLocal<String>()

    fun setDatabaseType(databaseType: String) {
        contextHolder.set(databaseType)
    }

    fun getDatabaseType(): String? {
        return contextHolder.get()
    }

    fun clear() {
        contextHolder.remove()
    }
}