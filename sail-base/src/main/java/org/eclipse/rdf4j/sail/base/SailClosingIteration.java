/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.sail.base;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.Iteration;
import org.eclipse.rdf4j.common.iteration.IterationWrapper;
import org.eclipse.rdf4j.sail.SailException;

/**
 * An {@link Iteration} that holds on to a {@link SailClosable} until the Iteration is closed. Upon closing, the
 * underlying Iteration is closed before the lock is released. This iterator closes itself as soon as all elements have
 * been read.
 * 
 * @author James Leigh
 */
abstract class SailClosingIteration<T, X extends Exception> extends IterationWrapper<T, X> {

	/**
	 * Creates a new {@link Iteration} that automatically closes the given {@link SailClosable}s.
	 * 
	 * @param iter   The underlying Iteration, must not be <tt>null</tt>.
	 * @param closes The {@link SailClosable}s to {@link SailClosable#close()} when the itererator is closed.
	 * @return a {@link CloseableIteration} that closes the given {@link SailClosable}
	 */
	public static <E> SailClosingIteration<E, SailException> makeClosable(
			CloseableIteration<? extends E, SailException> iter, SailClosable... closes) {
		return new SailClosingIteration<E, SailException>(iter, closes) {

			@Override
			protected void handleSailException(SailException e)
					throws SailException {
				throw e;
			}
		};
	}

	/*-----------*
	 * Variables *
	 *-----------*/

	/**
	 * The lock to release when the Iteration is closed.
	 */
	private final SailClosable[] closes;

	/*--------------*
	 * Constructors *
	 *--------------*/

	/**
	 * Creates a new {@link Iteration} that automatically closes the given {@link SailClosable}s.
	 * 
	 * @param iter   The underlying Iteration, must not be <tt>null</tt>.
	 * @param closes The {@link SailClosable}s to {@link SailClosable#close()} when the itererator is closed.
	 */
	public SailClosingIteration(CloseableIteration<? extends T, X> iter, SailClosable... closes) {
		super(iter);
		this.closes = closes;
	}

	/*---------*
	 * Methods *
	 *---------*/

	@Override
	public boolean hasNext()
			throws X {
		if (isClosed()) {
			return false;
		}

		boolean result = super.hasNext();
		if (!result) {
			close();
		}
		return result;
	}

	@Override
	public T next()
			throws X {
		if (isClosed()) {
			throw new NoSuchElementException("Iteration has been closed");
		}
		try {
			return super.next();
		} catch (NoSuchElementException e) {
			close();
			throw e;
		}
	}

	@Override
	public void remove()
			throws X {
		if (isClosed()) {
			throw new IllegalStateException();
		}
		try {
			super.remove();
		} catch (IllegalStateException e) {
			close();
			throw e;
		}
	}

	@Override
	protected void handleClose()
			throws X {
		try {
			super.handleClose();
		} finally {
			// Attempt to call close more often on all SailClosable instances by delaying handling
			List<SailException> exceptions = new ArrayList<>();
			List<Throwable> extraOrdinaryExceptions = new ArrayList<>();
			for (SailClosable closing : closes) {
				try {
					closing.close();
				} catch (SailException e) {
					exceptions.add(e);
				}
				// Delay all other exceptions also, but don't pass them through the handler
				catch (Throwable e) {
					extraOrdinaryExceptions.add(e);
				}
			}
			for (SailException nextException : exceptions) {
				handleSailException(nextException);
			}
			if (!extraOrdinaryExceptions.isEmpty()) {
				throw new UndeclaredThrowableException(extraOrdinaryExceptions.get(0));
			}
		}
	}

	/**
	 * Handler for exceptions generated by the closure of the {@link SailClosable} array given to this object. <br/>
	 * This method is called after all of the {@link SailClosable} objects have had close called on them.
	 * 
	 * @param e The {@link SailException} to handle.
	 * @throws X Instances of this generic-typed exception in response to the given {@link SailException} if the handler
	 *           decides to propagate the exception.
	 */
	protected abstract void handleSailException(SailException e)
			throws X;
}
