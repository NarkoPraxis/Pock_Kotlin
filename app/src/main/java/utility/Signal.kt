package utility

class Signal<T> {
    private val listeners = mutableListOf<(T) -> Unit>()

    fun connect(listener: (T) -> Unit) {
        listeners.add(listener)
    }

    fun disconnect(listener: (T) -> Unit) {
        listeners.remove(listener)
    }

    fun emit(value: T) {
        listeners.toList().forEach { it(value) }
    }
}
