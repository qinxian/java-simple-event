package qiangeng.event;

public abstract class BatchPublisher extends Publisher {
	/**
	 * Same as
	 * 
	 * <pre>
	 * next(1);
	 * </pre>
	 * 
	 */
	public long next() {
		return next(1);
	}

	abstract public long next(long batch);

	/**
	 * Same as
	 * 
	 * <pre>
	 * publish(endOrdinal, 1);
	 * </pre>
	 */
	public void publish(long endOrdinal) {
		publish(endOrdinal, 1);
	}

	abstract public void publish(long endOrdinal, long batch);

}
