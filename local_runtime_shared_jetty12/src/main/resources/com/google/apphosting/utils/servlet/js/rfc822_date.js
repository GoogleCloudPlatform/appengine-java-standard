/**
 Copyright 2021 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
**/

var RFC822Date = {};

/**
 * Return a DateTime in RFC822 format.
 * @see http://www.w3.org/Protocols/rfc822/#z28
 * @param {Date} date A Date object.
 * @param {string} opt_tzo The timezone offset.
 */
RFC822Date.format = function(date, opt_tzo) {
  var tzo = opt_tzo || RFC822Date.getTZO(date.getTimezoneOffset());
  var rfc822Date = RFC822Date.DAYS[date.getDay()] + ', ';
  rfc822Date += RFC822Date.padZero(date.getDate()) + ' ';
  rfc822Date += RFC822Date.MONTHS[date.getMonth()] + ' ';
  rfc822Date += date.getFullYear() + ' ';
  rfc822Date += RFC822Date.padZero(date.getHours()) + ':';
  rfc822Date += RFC822Date.padZero(date.getMinutes()) + ':';
  rfc822Date += RFC822Date.padZero(date.getSeconds()) + ' ' ;
  rfc822Date += tzo;
  return rfc822Date;
};


/**
 * @type {Array}
 */
RFC822Date.MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                     'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];


/**
 * @type {Array}
 */
RFC822Date.DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];


/**
 * Pads a value with a 0 if it is less than 10;
 * @param {number|string}
 * @return {string}
 */
RFC822Date.padZero = function(val) {
  val = val + ''; // cast into string
  if (val.length < 2) {
    val = '0' + val;
  }
  return val;
};


/**
 * Returns a timezone offset in the format +|-dddd.
 * @param {string} tzo A time zone offset from GMT in minutes.
 * @return {string} The time zone offset as a string.
 */
RFC822Date.getTZO = function(tzo) {
  var hours = Math.floor(tzo / 60);
  var tzoFormatted = hours > 0 ? '-' : '+';

  var absoluteHours = Math.abs(hours);
  tzoFormatted += absoluteHours < 10 ? '0' : '';
  tzoFormatted += absoluteHours;

  var moduloMinutes = Math.abs(tzo % 60);
  tzoFormatted += moduloMinutes == 0 ? '00' : moduloMinutes

  return tzoFormatted;
};

