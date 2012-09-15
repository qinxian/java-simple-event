package qiangeng.event;

import static qiangeng.common.Util.unsafe;
import static qiangeng.event.ShareLongArray.shareAddress;
import static qiangeng.event.ShareLongArray.shareIndex;

public class AbstractEventBus {
	public static final long INITIAL = -1;
	private final int bufferSize;
	private final Retry retry;
	private long[] array;

	public AbstractEventBus(int bufferSizeBits) {
		int size = 1 << bufferSizeBits;
		bufferSize = size;
		retry = Retry.YIELD;
	}

	protected long[] createArray(int length) {
		long[] array = new long[shareIndex(length)];
		for (int i = 0; i < length; i++) {
			array[shareIndex(i)] = INITIAL;
		}
		this.array = array;
		return array;
	}

	protected Publisher createSinglePublisher(int firstIndex, final int lastIndex) {
		final long firstAddress = shareAddress(firstIndex);
		return new BatchPublisher() {
			private long workOrdinal = INITIAL;
			private long minOrdinal = INITIAL;

			@Override
			public long next(long batch) {
				long end = workOrdinal + batch;
				workOrdinal = end;
				long wrap = end - bufferSize;
				if (wrap > minOrdinal) minOrdinal = checkMinimumOrdinal(wrap, lastIndex);
				return end;
			}

			@Override
			public void publish(long endOrdinal, long batch) {
				unsafe.putLongVolatile(array, firstAddress, endOrdinal);
			}
		};
	}

	protected long checkMinimumOrdinal(long wrapOrdinal, int dependIndex) {
		final long dependsAddress = shareAddress(dependIndex);
		final long[] array = this.array;
		long min;
		while (wrapOrdinal > (min = unsafe.getLongVolatile(array, dependsAddress))) {
			Thread.yield();
			// LockSupport.parkNanos(1);
		}
		return min;
	}

	protected Publisher createSharePublisher(final int firstIndex, final int groups, final int groupLength) {
		return new Publisher() {
			private int currentIndex = -1;

			@Override
			public long next() {
				final int series = groupLength - 1;
				while (true) {
					int index = firstIndex;
					long maxOrdinal = unsafe.getLongVolatile(array, shareAddress(index));
					long result = unsafe.getLongVolatile(array, shareAddress(index + series));
					long minSize = maxOrdinal - result;
					int minIndex = 0;
					long size;
					long max;
					for (int i = 1; i < groups; i++) {
						index += groupLength;
						max = unsafe.getLongVolatile(array, shareAddress(index));
						result = unsafe.getLongVolatile(array, shareAddress(index + series));
						size = max - result;
						if (size < minSize) {
							minSize = size;
							minIndex = i;
							maxOrdinal = max;
						}
					}
					if (minSize > bufferSize) continue;
					currentIndex = minIndex;
					return (maxOrdinal + 1) * bufferSize + minIndex;
				}
			}

			@Override
			public void publish(long endOrdinal) {
				long ordinal = (endOrdinal - currentIndex) / bufferSize;
				unsafe.putLongVolatile(array, shareAddress(currentIndex * groupLength), ordinal);
			}
		};
	}

	protected Publisher createEquallyPublisher(final int firstIndex, final int groups, final int groupLength) {
		return new Publisher() {
			private long workOrdinal = INITIAL;
			private long minOrdinal = INITIAL;

			@Override
			public long next() {
				long end = workOrdinal + 1;
				workOrdinal = end;
				int index = (int) (end % groups);
				long endOrdinal = end / groups;
				// System.out.printf("index=%s, end=%s, offset=%s\n", index, endOrdinal, (1 + index) * groupLength - 1);
				long wrap = endOrdinal - bufferSize;
				if (wrap > minOrdinal) {
					minOrdinal = checkMinimumOrdinal(wrap, (1 + index) * groupLength - 1);
				}
				return end;
			}

			@Override
			public void publish(long endOrdinal) {
				int index = (int) (endOrdinal % groups);
				unsafe.putLongVolatile(array, shareAddress(index * groupLength + firstIndex), endOrdinal / groups);
			}
		};
	}

	// public void terminate(long value) {
	// unsafe.putLongVolatile(array, firstAddress, value);
	// }

	protected static boolean isTerminated(long[] array, long maxOrdinalAddress) {
		return (unsafe.getLongVolatile(array, maxOrdinalAddress) == -2);
	}

	protected Thread createDispatchThread(ThreadGroup group, final long firstAddress, final int index,
			final Dispatcher dispatcher) {
		return new Thread(group, "EventBus-dispather-" + index) {
			public void run() {
				execute(array, firstAddress, shareAddress(index), shareAddress(index + 1), bufferSize, retry,
						dispatcher);
			}
		};
	}

	protected Thread createDispatchThread(ThreadGroup group, final int index, final Dispatcher dispatcher,
			final int groups, final int groupLength) {
		return new Thread(group, "EventBus-dispather-" + index) {
			public void run() {
				execute(array, index, index + 1, bufferSize, retry, dispatcher, groups, groupLength);
			}
		};
	}

	protected static void execute(final long[] array, final long maxOrdinalAddress, final long dependsAddress,
			final long workAddress, final int bufferSize, Retry retry, Dispatcher dispatcher) {
		long next = unsafe.getLongVolatile(array, workAddress) + 1;
		final long initialCount = retry.getInitialCount();
		while (!dispatcher.isDone()) {
			long count = initialCount;
			long end;
			while ((end = unsafe.getLongVolatile(array, dependsAddress)) < next) {
				count = retry.check(count);
				// if (isTerminated(array, maxOrdinalAddress)) return;
				if (dispatcher.isDone()) return;
			}
			dispatcher.dispatch(bufferSize, next, end);
			unsafe.putLongVolatile(array, workAddress, end++);
			next = end;
			// if (isTerminated(array, maxOrdinalAddress)) return;
		}
	}

	protected static void execute(final long[] array, final int dependIndex, final int workIndex, final int bufferSize,
			Retry retry, Dispatcher dispatcher, final int groups, final int groupLength) {
		final long initialCount = retry.getInitialCount();
		long count = initialCount;
		while (!dispatcher.isDone()) {
			for (int i = 0, offset = 0; i < groups; i++, offset += groupLength) {
				long workAddress = shareAddress(workIndex + offset);
				long next = unsafe.getLongVolatile(array, workAddress) + 1;

				long dependsAddress = shareAddress(dependIndex + offset);
				long end = unsafe.getLongVolatile(array, dependsAddress);
				if (end < next) {
					if (dispatcher.isDone()) return;
					continue;
				}
				dispatcher.dispatch(bufferSize, next, end);
				unsafe.putLongVolatile(array, workAddress, end);
				if (count != initialCount) count = initialCount;
			}
			count = retry.check(count);
		}
	}
}
