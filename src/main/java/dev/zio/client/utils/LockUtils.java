/*
 * ─────────────────────────────────────────────
 * Licensed under the MIT License.
 *
 * Zio is not affiliated with or endorsed by Mio.
 * Provided "as is", without warranty of any kind.
 * ─────────────────────────────────────────────
 */

package dev.zio.client.utils;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class LockUtils {
   private static final Lock SHARED_LOCK = new ReentrantLock();

   public static void runWithSharedLock(Runnable action) {
      runWithLock(SHARED_LOCK, action);
   }

   public static Runnable wrapWithSharedLock(Runnable action) {
      return () -> runWithSharedLock(action);
   }

   public static void runWithLock(Lock suppliedLock, Runnable action) {
      try {
         suppliedLock.lock();
         action.run();
      } finally {
         suppliedLock.unlock();
      }
   }

   public static Runnable wrapWithLock(Lock suppliedLock, Runnable action) {
      return () -> runWithLock(suppliedLock, action);
   }

}
