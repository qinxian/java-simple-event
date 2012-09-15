package qiangeng.event;

import java.util.concurrent.locks.LockSupport;

public enum Retry {
	BusySpin, SLEEPING {
		@Override
		public long check(long count) {
			if (count > 100) return --count;
			if (count <= 0) LockSupport.parkNanos(1L);
			else {
				--count;
				Thread.yield();
			}
			return count;
		}

		@Override
		public long getInitialCount() {
			return 200;
		}
	},
	YIELD {
		@Override
		public long check(long count) {
			if (0 != count) return --count;
			Thread.yield();
			return count;
		}

		@Override
		public long getInitialCount() {
			return 100;
		}
	};

	public long getInitialCount() {
		return 0;
	}

	public long check(long count) {
		return 0;
	}

	public boolean await(long time) {
		return System.currentTimeMillis() < time;
	}

}
