package qiangeng.event.test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import qiangeng.event.ShareLongArray;
import qiangeng.event.Util;

public class CompareTest {

	public static void main(String[] args) throws Exception {
		int[] ints = new int[] { 1, 2, 3 };
		System.out.printf("hash: %X, %X\n", ints.getClass().hashCode(), ints.hashCode());
		// ClassPointer
		System.out.printf("%s\n", Util.unsafe.getInt(ints, 0L));
		// Flags
		System.out.printf("%s\n", Util.unsafe.getInt(ints, 4L));
		// HashCode, isArray
		System.out.printf("%X\n", Util.unsafe.getInt(ints, 8L));
		// length
		System.out.printf("%X\n", Util.unsafe.getInt(ints, 12L));
		System.out.printf("%s\n", Util.unsafe.getInt(ints, 16L));
		System.out.printf("%s\n", Util.unsafe.getInt(ints, 20L));
		System.out.printf("%s\n", Util.unsafe.getInt(ints, 24L));
		for (int x = 0; x < 1; x++) {
			long[] longs = new long[] { 1, 2, 3 };
			System.out.printf("%s\n", Util.unsafe.getInt(longs, 0L));
			System.out.printf("%s\n", Util.unsafe.getInt(longs, 4L));
			System.out.printf("%X\n", Util.unsafe.getInt(longs, 8L));
			System.out.printf("%X\n", Util.unsafe.getInt(longs, 12L));
			for (int i = 0; i < 3; i++)
				System.out.printf("%s, %s\n", ShareLongArray.address(i),
						Util.unsafe.getLong(longs, ShareLongArray.address(i)));
		}
		for (int i = 0; i < 30; i++)
			test();
	}

	static void test() throws InterruptedException {
		final AtomicLong counter = new AtomicLong();
		final AtomicLong mirror = new AtomicLong();
		final CountDownLatch latch = new CountDownLatch(2);
		Thread[] threads = new Thread[2];
		for (int i = 0; i < 2; i++)
			threads[i] = new Thread() {
				@Override
				public void run() {
					for (int i = 0; i < (1 << 20); i++) {
						long id = counter.incrementAndGet();
						long last = id - 1;
						// while (mirror.get() != last);
						// mirror.set(id);
						while (!mirror.compareAndSet(last, id))
							;
					}
					latch.countDown();
				}
			};
		long time = System.nanoTime();
		for (Thread thread : threads)
			thread.start();
		latch.await();
		time = System.nanoTime() - time;
		time = TimeUnit.NANOSECONDS.toMillis(time);
		// System.out.printf("volatile: %,d,\t\t\t %,d\n", time, (1 << 20) / time);
		System.gc();
		final CountDownLatch latch2 = new CountDownLatch(2);
		for (int i = 0; i < 2; i++) {
			threads[i] = new Thread() {
				public void run() {
					for (int i = 0; i < (1 << 20); i++) {
						synchronized (counter) {
							long id = counter.incrementAndGet();
							mirror.set(id);
						}
					}
					latch2.countDown();
				}
			};
		}
		long time2 = System.nanoTime();
		for (Thread thread : threads)
			thread.start();
		latch2.await();
		time2 = System.nanoTime() - time2;
		time2 = TimeUnit.NANOSECONDS.toMillis(time2);
		// System.out.printf("synchron: %,d\t\t %,d\n", time2, (1 << 20) / time2);

		System.gc();
		final CountDownLatch latch3 = new CountDownLatch(2);
		final Semaphore sem = new Semaphore(1);
		for (int i = 0; i < 2; i++) {
			threads[i] = new Thread() {
				public void run() {
					for (int i = 0; i < (1 << 20); i++) {
						try {
							sem.acquire();
							long id = counter.incrementAndGet();
							mirror.set(id);
						} catch (InterruptedException e) {
							e.printStackTrace();
						} finally {
							sem.release();
						}
					}
					latch3.countDown();
				}
			};
		}
		long time3 = System.nanoTime();
		for (Thread thread : threads)
			thread.start();
		latch3.await();
		time3 = System.nanoTime() - time3;
		time3 = TimeUnit.NANOSECONDS.toMillis(time3);

		long iterations = (1 << 20);
		System.out.printf("voltile, sync: lock:  \t%,d \t%,d \t %,d\n", iterations / time, iterations / time2,
				iterations / time3);
	}
}
