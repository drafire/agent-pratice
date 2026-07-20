/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.drafire.data;

import java.time.LocalDate;

public class Booking {

	private String bookingNumber;

	private LocalDate date;

	private LocalDate bookingTo;

	private String customerName;

	private String origin;

	private String destination;

	private BookingStatus bookingStatus;

	private BookingClass bookingClass;

	public Booking(String bookingNumber, LocalDate date, String customerName, BookingStatus bookingStatus, String origin,
			String destination, BookingClass bookingClass) {
		this.bookingNumber = bookingNumber;
		this.date = date;
		this.customerName = customerName;
		this.bookingStatus = bookingStatus;
		this.origin = origin;
		this.destination = destination;
		this.bookingClass = bookingClass;
	}

	public String getBookingNumber() {
		return bookingNumber;
	}

	public void setBookingNumber(String bookingNumber) {
		this.bookingNumber = bookingNumber;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public LocalDate getBookingTo() {
		return bookingTo;
	}

	public void setBookingTo(LocalDate bookingTo) {
		this.bookingTo = bookingTo;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public BookingStatus getBookingStatus() {
		return bookingStatus;
	}

	public void setBookingStatus(BookingStatus bookingStatus) {
		this.bookingStatus = bookingStatus;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getDestination() {
		return destination;
	}

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public BookingClass getBookingClass() {
		return bookingClass;
	}

	public void setBookingClass(BookingClass bookingClass) {
		this.bookingClass = bookingClass;
	}

}