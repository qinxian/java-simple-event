package qiangeng.event.test;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import qiangeng.event.Dispatcher;
import qiangeng.event.EventBus;
import qiangeng.event.Publisher;

//java -classpath E:\java\junit.jar;E:\JAVA\test\jmock-2.6-rc2\hamcrest-core-1.3.0RC1.jar;F:\qiangeng\work\build\classes qiangeng.event.bus.test.EventBusTest
public class EventBusTest {
	public static void main(String[] args) throws Exception {
		new EventBusTest().test1();
	}

	@Test
	public void test1() throws Exception {
		final int SERIES = 3;
		final int RUNS = 10;
		final int GROUPS = 6;
		final long ITERATIONS = 1 << 20;
		for (int d = 1; d <= SERIES; d++) {
			System.out.printf("test batch %s\n", d);
			for (int i = 0; i < RUNS; i++) {
				singleSeries(d, ITERATIONS);
				System.gc();
			}
			for (int x = 2; x < GROUPS; x++) {
				for (int i = 0; i < RUNS; i++) {
					groupSeries(x, d, ITERATIONS);
					System.gc();
				}
				for (int i = 0; i < RUNS; i++) {
					testBalancePublisher(x, ITERATIONS, d);
					System.gc();
				}
				for (int i = 0; i < RUNS; i++) {
					testEquallyPublisher(x, ITERATIONS, d);
					System.gc();
				}
			}
		}
	}

	static class TestDispatcher implements Dispatcher {
		volatile boolean done;
		long count;
		final long mask;
		final long iterations;

		public TestDispatcher(long iterations) {
			this.iterations = iterations;
			mask = iterations - 1;
		}

		@Override
		public boolean isDone() {
			return done;
		}

		@Override
		public void dispatch(int bufferSize, long start, long end) {
			// System.out.printf("count: %s, %s-%s\n", count, start, end);
			while (start++ <= end)
				count++;
			if (end == mask) {
				assertEquals(count, iterations);
				done = true;
			}
		}
	}

	void singleSeries(final int series, final long iterations) throws InterruptedException {
		EventBus bus = new EventBus(13);
		Dispatcher[] dispatchers = new Dispatcher[series];
		for (int i = 0; i < series; i++)
			dispatchers[i] = new TestDispatcher(iterations);
		Publisher publisher = bus.dispatch(dispatchers);
		ThreadGroup group = bus.getThreadGroup();
		Thread[] threads = new Thread[group.activeCount()];

		long time = System.nanoTime();
		for (int i = 0; i < iterations; i++) {
			long id = publisher.next();
			publisher.publish(id);
		}

		group.enumerate(threads, false);
		for (Thread thread : threads)
			if (thread != null) thread.join();
		time = System.nanoTime() - time;
		long millis = TimeUnit.NANOSECONDS.toMillis(time);
		System.out.printf("series: %s, time: %,d ms, %,d ops/ms\n", series, millis, iterations / millis);
	}

	static Thread[] createPublishers(final long iterations, Publisher[] publishers) {
		int count = publishers.length;
		Thread[] threads = new Thread[count];
		for (int i = 0; i < count; i++) {
			final Publisher publisher = publishers[i];
			// final int g = i;
			threads[i] = new Thread() {
				@Override
				public void run() {
					for (int i = 0; i < iterations; i++) {
						long id = publisher.next();
						// System.out.printf("publish group: %s, %s\n", g, id);
						publisher.publish(id);
					}
				}
			};
		}
		return threads;
	}

	static class GroupDispatcher implements Dispatcher {
		volatile boolean done;
		long count;
		final long mask;
		final long iterations;

		public GroupDispatcher(int group, long iterations) {
			this.iterations = group * iterations;
			mask = this.iterations - 1;
		}

		@Override
		public boolean isDone() {
			return done;
		}

		@Override
		public void dispatch(int bufferSize, long start, long end) {
			// System.out.printf("count: %s, %s-%s\n", count, start, end);
			while (start++ <= end)
				count++;
			if (count == iterations) {
				done = true;
			}
		}
	}

	void groupSeries(int groups, final int series, final long iterations) throws InterruptedException {
		EventBus bus = new EventBus(13);
		Dispatcher[] dispatchers = new Dispatcher[series];
		for (int i = 0; i < series; i++)
			dispatchers[i] = new GroupDispatcher(groups, iterations);
		Publisher[] publishers = bus.dispatch(groups, dispatchers);
		ThreadGroup threadGroup = bus.getThreadGroup();
		Thread[] threads = new Thread[threadGroup.activeCount()];

		long time = System.nanoTime();
		for (Thread thread : createPublishers(iterations, publishers))
			thread.start();
		threadGroup.enumerate(threads, false);
		for (Thread thread : threads)
			if (thread != null) thread.join();
		time = System.nanoTime() - time;
		long millis = TimeUnit.NANOSECONDS.toMillis(time);
		System.out.printf("groups %s, series: %s, time: %,d ms, %,d ops/ms\n", groups, series, millis, iterations
				/ millis);
	}

	static class GroupDispatcher2 implements Dispatcher {
		volatile boolean done;
		final AtomicLong count;
		final long amount;

		public GroupDispatcher2(AtomicLong counter, long amount) {
			this.count = counter;
			this.amount = amount;
		}

		@Override
		public boolean isDone() {
			return done;
		}

		@Override
		public void dispatch(int bufferSize, long start, long end) {
			// System.out.printf("count: %s, %s-%s\n", count, start, end);
			while (start++ <= end)
				count.incrementAndGet();
			if (count.get() == amount) {
				done = true;
			}
		}
	}

	public void testBalancePublisher(int groups, long iterations, int series) throws Exception {
		EventBus bus = new EventBus(13);
		Dispatcher[] dispatchers = new Dispatcher[series];
		for (int i = 0; i < series; i++)
			dispatchers[i] = new GroupDispatcher2(new AtomicLong(), iterations);
		Publisher publisher = bus.dispatchBalance(groups, dispatchers);

		long time = System.nanoTime();

		for (int i = 0; i < iterations; i++) {
			long id = publisher.next();
			publisher.publish(id);
		}

		ThreadGroup threadGroup = bus.getThreadGroup();
		Thread[] threads = new Thread[threadGroup.activeCount()];
		threadGroup.enumerate(threads, false);
		for (Thread thread : threads)
			if (thread != null) thread.join();
		time = System.nanoTime() - time;
		long millis = TimeUnit.NANOSECONDS.toMillis(time);
		System.out.printf("balance groups %s, series: %s, time: %,d ms, %,d ops/ms\n", groups, series, millis,
				iterations / millis);
	}

	static class TestDispatcher2 implements Dispatcher {
		volatile boolean done;
		long count;
		final long mask;
		final long iterations;

		public TestDispatcher2(long iterations) {
			this.iterations = iterations;
			mask = iterations - 1;
		}

		@Override
		public boolean isDone() {
			return done;
		}

		@Override
		public void dispatch(int bufferSize, long start, long end) {
			// System.out.printf("count: %s, %s-%s\n", count, start, end);
			while (start++ <= end)
				count++;
			if (count == iterations) {
				done = true;
			}
		}
	}

	public void testEquallyPublisher(int groups, long iterations, int series) throws Exception {
		EventBus bus = new EventBus(13);
		iterations *= groups;
		Dispatcher[] dispatchers = new Dispatcher[series];
		for (int i = 0; i < series; i++)
			dispatchers[i] = new TestDispatcher2(iterations);
		Publisher publisher = bus.dispatchEqually(groups, dispatchers);

		long time = System.nanoTime();

		for (int i = 0; i < iterations; i++) {
			long id = publisher.next();
			publisher.publish(id);
		}

		ThreadGroup threadGroup = bus.getThreadGroup();
		Thread[] threads = new Thread[threadGroup.activeCount()];
		threadGroup.enumerate(threads, false);
		for (Thread thread : threads)
			if (thread != null) thread.join();
		time = System.nanoTime() - time;
		long millis = TimeUnit.NANOSECONDS.toMillis(time);
		System.out.printf("equally groups %s, series: %s, time: %,d ms, %,d ops/ms\n", groups, series, millis,
				iterations / millis);
	}

}
