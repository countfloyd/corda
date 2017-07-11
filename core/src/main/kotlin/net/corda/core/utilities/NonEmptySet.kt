package net.corda.core.utilities

import com.google.common.collect.Iterators
import java.util.*
import java.util.function.Consumer
import java.util.stream.Stream

/**
 * An immutable ordered non-empty set.
 */
class NonEmptySet<T> private constructor(private val elements: Set<T>) : Set<T> {
    companion object {
        /**
         * Returns a singleton set containing [element]. This behaves the same as [Collections.singleton] but returns a
         * [NonEmptySet] for the extra type-safety.
         */
        @JvmStatic
        fun <T> of(element: T): NonEmptySet<T> = NonEmptySet(Collections.singleton(element))

        /** Returns a non-empty set containing the given elements, minus duplicates, in the order each was specified. */
        @JvmStatic
        fun <T> of(first: T, second: T, vararg rest: T): NonEmptySet<T> {
            val elements = LinkedHashSet<T>(rest.size + 2)
            elements += first
            elements += second
            elements.addAll(rest)
            return NonEmptySet(elements)
        }

        /**
         * Returns a non-empty set containing each of [elements], minus duplicates, in the order each appears first in
         * the source collection.
         * @throws IllegalArgumentException If [elements] is empty.
         */
        @JvmStatic
        fun <T> copyOf(elements: Collection<T>): NonEmptySet<T> {
            if (elements is NonEmptySet) return elements
            return when (elements.size) {
                0 -> throw IllegalArgumentException("elements is empty")
                1 -> of(elements.first())
                else -> {
                    val copy = LinkedHashSet<T>(elements.size)
                    elements.forEach { copy += it }  // Set.addAll doesn't give any guarantees on which duplicate elements are dropped
                    NonEmptySet(copy)
                }
            }
        }
    }

    /** Returns the first element of the set. */
    fun head(): T = elements.iterator().next()
    override val size: Int get() = elements.size
    override fun contains(element: T): Boolean = element in elements
    override fun containsAll(elements: Collection<T>): Boolean = this.elements.containsAll(elements)
    override fun isEmpty(): Boolean = false
    override fun iterator(): Iterator<T> = Iterators.unmodifiableIterator(elements.iterator())
    override fun forEach(action: Consumer<in T>) = elements.forEach(action)
    override fun stream(): Stream<T> = elements.stream()
    override fun parallelStream(): Stream<T> = elements.parallelStream()
    override fun spliterator(): Spliterator<T> = elements.spliterator()
    override fun equals(other: Any?): Boolean = other === this || other == elements
    override fun hashCode(): Int = elements.hashCode()
    override fun toString(): String = elements.toString()
}
