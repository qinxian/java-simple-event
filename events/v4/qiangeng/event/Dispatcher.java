package qiangeng.event;

public interface Dispatcher {
	boolean isDone();

	/**
	 * <pre>
	 * while (start &lt; end)
	 * 	handleEvent(start++);
	 * handleEvent(start);
	 * </pre>
	 * 
	 * @param bufferSize
	 * @param start
	 * @param end
	 */
	void dispatch(int bufferSize, long start, long end);
}
