package com.recargapay.wallet.support;

import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

public final class ConcurrentCallers {

	private ConcurrentCallers() {
	}

	public static List<Response> runConcurrently(int callers, IntFunction<Response> action) throws Exception {
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<Response>> requests = new ArrayList<>();
			for (int caller = 0; caller < callers; caller++) {
				int index = caller;
				requests.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return action.apply(index);
				}));
			}
			if (!ready.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Callers did not become ready");
			}
			start.countDown();
			List<Response> responses = new ArrayList<>();
			for (Future<Response> request : requests) {
				responses.add(request.get(90, TimeUnit.SECONDS));
			}
			return responses;
		}
	}
}
