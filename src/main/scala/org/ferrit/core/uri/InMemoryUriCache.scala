package org.ferrit.core.uri

/**
 * A simple non-threadsafe URI cache that merely wraps a Scala Set
 * using the URI hashcode as item identifier.
 * The new size returned after a put operation is not guaranteed 
 * correct if concurrent puts are made.
 * 
 * Possible future enhancements:
 *
 * <ul>
 *   <li>Replace hash code store with SHA1
 *   <li>Convert to actor if multiple crawler workers would wish to make puts
 * </ul>
 */
class InMemoryUriCache extends UriCache {
  private var cache: Set[Integer] = Set.empty

  override def size: Int = cache.size

  override def put(uri: CrawlUri): Int = {
    val newCache = cache + uri.hashCode
    val size = newCache.size
    cache = newCache
    size
  }

  override def contains(uri: CrawlUri): Boolean = cache.contains(uri.hashCode)
}