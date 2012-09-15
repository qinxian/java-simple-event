package qiangeng.event.test;

import java.util.Date;

import org.junit.Test;

import qiangeng.event.Dispatcher;
import qiangeng.event.EventBus;
import qiangeng.event.Publisher;

public class LoggerTest {
	Log log = new Log();

	@Test
	public void test1() throws Exception {
		log.log(Level.INFO, "%s\n", "test1");
		for (int i = 0; i < 10000; i++)
			test2();
		// Thread.sleep(1000);
	}

	@Test
	public void testNoEventBus() throws Exception {
		for (int i = 0; i < 10001; i++)
			new Entry().set(Level.DEBUG, "%s\n", "test2").print(i);
	}

	void test2() {
		log.log(Level.DEBUG, "%s\n", "test2");
	}

	static enum Level {
		TRACE, DEBUG, INFO, WARN, ERROR, FATAL;
	}

	public static class Entry {
		Level level;
		String format;
		Object[] arguments;

		public Entry set(Level level, String format, Object... arguments) {
			this.level = level;
			this.format = format;
			this.arguments = arguments;
			return this;
		}

		public void print(int id) {
			System.out.print(id);
			System.out.print(":  ");
			System.out.print(level);
			System.out.print(":  ");
			System.out.print(new Date());
			System.out.print(":  ");
			System.out.printf(format, arguments);
		}
	}

	public static class Log {
		private final Entry[] buffer;

		private final Publisher publisher;

		public Log() {
			EventBus bus = new EventBus(8);
			int size = 1 << 8;
			buffer = new Entry[size];
			for (int i = 0; i < size; i++)
				buffer[i] = new Entry();
			publisher = bus.dispatch(new LogHandler(buffer));
		}

		public void log(Level level, String format, Object... arguments) {
			synchronized (publisher) {
				long id = publisher.next();
				buffer[(int) (id & buffer.length - 1)].set(level, format, arguments);
				publisher.publish(id);
			}
		}
	}

	public static class LogHandler implements Dispatcher {
		private final Entry[] buffer;

		public LogHandler(Entry[] buffer) {
			this.buffer = buffer;
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public void dispatch(int bufferSize, long start, long end) {
			while (start <= end) {
				int id = (int) start;
				Entry entry = buffer[(bufferSize - 1) & id];
				entry.print(id);
				start++;
			}
		}
	}
}
