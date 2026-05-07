package utility

expect object PlatformStorage {
    fun initialize(context: Any?)
    fun saveInt(store: String, key: String, value: Int)
    fun getInt(store: String, key: String, default: Int): Int
    fun saveString(store: String, key: String, value: String)
    fun getString(store: String, key: String, default: String): String
    fun saveBoolean(store: String, key: String, value: Boolean)
    fun getBoolean(store: String, key: String, default: Boolean): Boolean
    fun saveLong(store: String, key: String, value: Long)
    fun getLong(store: String, key: String, default: Long): Long
    fun saveFloat(store: String, key: String, value: Float)
    fun getFloat(store: String, key: String, default: Float): Float
    fun currentTimeMs(): Long
}
