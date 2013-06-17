package qiangeng.event;

import static qiangeng.event.Util.unsafe;

public class ShareLongArray {
	private static final int base = unsafe.arrayBaseOffset(long[].class);
	private static final int shift;
	protected static final int spacing = 16;
	protected static final int padding = 16;

	static {
		int scale = unsafe.arrayIndexScale(long[].class);
		if ((scale & (scale - 1)) != 0) throw new Error("data type scale not a power of two");
		shift = 31 - Integer.numberOfLeadingZeros(scale);
	}

	/**
	 * Avoid False Sharing, by method of Nicholas Butler {@link http://www.nickbutler.net/Article/FalseSharing}
	 */
	public static int shareIndex(int i) {
		return padding + spacing * i;
	}

	public static long shareAddress(int i) {
		return address(shareIndex(i));
	}

	public static long address(long index) {
		return (index << shift) + base;
	}

	public static long getAndSet(final long[] array, final long offset, final long newValue) {
		do {
			long current = unsafe.getLongVolatile(array, offset);
			if (unsafe.compareAndSwapLong(array, offset, current, newValue)) return current;
		} while (true);
	}

	public static long getAndAdd(final long[] array, final long offset, final long delta) {
		do {
			long current = unsafe.getLongVolatile(array, offset);
			if (unsafe.compareAndSwapLong(array, offset, current, current + delta)) return current;
		} while (true);
	}

	public static long addAndGet(final long[] array, final long offset, long delta) {
		do {
			long current = unsafe.getLongVolatile(array, offset);
			long next = current + delta;
			if (unsafe.compareAndSwapLong(array, offset, current, next)) return next;
		} while (true);
	}

}
