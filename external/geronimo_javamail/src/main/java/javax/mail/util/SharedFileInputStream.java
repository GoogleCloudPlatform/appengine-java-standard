/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package javax.mail.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import javax.mail.internet.SharedInputStream;

public class SharedFileInputStream extends BufferedInputStream implements SharedInputStream {


    // This initial size isn't documented, but bufsize is 2048 after initialization for the
    // Sun implementation.
    private static final int DEFAULT_BUFFER_SIZE = 2048;

    // the shared file information, used to synchronize opens/closes of the base file.
    private SharedFileSource source;

    /**
     * The file offset that is the first byte in the read buffer.
     */
    protected long bufpos;

    /**
     * The normal size of the read buffer.
     */
    protected int bufsize;

    /**
     * The size of the file subset represented by this stream instance.
     */
    protected long datalen;

    /**
     * The source of the file data.  This is shared across multiple
     * instances.
     */
    protected RandomAccessFile in;

    /**
     * The starting position of data represented by this stream relative
     * to the start of the file data.  This stream instance represents
     * data in the range start to (start + datalen - 1).
     */
    protected long start;


    /**
     * Construct a SharedFileInputStream from a file name, using the default buffer size.
     *
     * @param file   The name of the file.
     *
     * @exception IOException
     */
    public SharedFileInputStream(String file) throws IOException {
        this(file, DEFAULT_BUFFER_SIZE);
    }


    /**
     * Construct a SharedFileInputStream from a File object, using the default buffer size.
     *
     * @param file   The name of the file.
     *
     * @exception IOException
     */
    public SharedFileInputStream(File file) throws IOException {
        this(file, DEFAULT_BUFFER_SIZE);
    }


    /**
     * Construct a SharedFileInputStream from a file name, with a given initial buffer size.
     *
     * @param file       The name of the file.
     * @param bufferSize The initial buffer size.
     *
     * @exception IOException
     */
    public SharedFileInputStream(String file, int bufferSize) throws IOException {
        // I'm not sure this is correct or not.  The SharedFileInputStream spec requires this
        // be a subclass of BufferedInputStream.  The BufferedInputStream constructor takes a stream,
        // which we're not really working from at this point.  Using null seems to work so far.
        super(null);
        init(new File(file), bufferSize);
    }


    /**
     * Construct a SharedFileInputStream from a File object, with a given initial buffer size.
     *
     * @param file   The name of the file.
     * @param bufferSize The initial buffer size.
     *
     * @exception IOException
     */
    public SharedFileInputStream(File file, int bufferSize) throws IOException {
        // I'm not sure this is correct or not.  The SharedFileInputStream spec requires this
        // be a subclass of BufferedInputStream.  The BufferedInputStream constructor takes a stream,
        // which we're not really working from at this point.  Using null seems to work so far.
        super(null);
        init(file, bufferSize);
    }


    /**
     * Private constructor used to spawn off a shared instance
     * of this stream.
     *
     * @param source  The internal class object that manages the shared resources of
     *                the stream.
     * @param start   The starting offset relative to the beginning of the file.
     * @param len     The length of file data in this shared instance.
     * @param bufsize The initial buffer size (same as the spawning parent.
     */
    private SharedFileInputStream(SharedFileSource source, long start, long len, int bufsize) {
        super(null);
        this.source = source;
        in = source.open();
        this.start = start;
        bufpos = start;
        datalen = len;
        this.bufsize = bufsize;
        buf = new byte[bufsize];
        // other fields such as pos and count initialized by the super class constructor.
    }


    /**
     * Shared initializtion routine for the constructors.
     *
     * @param file       The file we're accessing.
     * @param bufferSize The initial buffer size to use.
     *
     * @exception IOException
     */
    private void init(File file, int bufferSize) throws IOException {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        // create a random access file for accessing the data, then create an object that's used to share
        // instances of the same stream.
        source = new SharedFileSource(file);
        // we're opening the first one.
        in = source.open();
        // this represents the entire file, for now.
        start = 0;
        // use the current file length for the bounds
        datalen = in.length();
        // now create our buffer version
        bufsize = bufferSize;
        bufpos = 0;
        // NB:  this is using the super class protected variable.
        buf = new byte[bufferSize];
    }


    /**
     * Check to see if we need to read more data into our buffer.
     *
     * @return False if there's not valid data in the buffer (generally means
     *         an EOF condition).
     * @exception IOException
     */
    private boolean checkFill() throws IOException {
        // if we have data in the buffer currently, just return
        if (pos < count) {
            return true;
        }

        // ugh, extending BufferedInputStream also means supporting mark positions.  That complicates everything.
        // life is so much easier if marks are not used....
        if (markpos < 0) {
            // reset back to the buffer position
            pos = 0;
            // this will be the new position within the file once we're read some data.
            bufpos += count;
        }
        else {
            // we have marks to worry about....damn.
            // if we have room in the buffer to read more data, then we will.  Otherwise, we need to see
            // if it's possible to shift the data in the buffer or extend the buffer (up to the mark limit).
            if (pos >= buf.length) {
                // the mark position is not at the beginning of the buffer, so just shuffle the bytes, leaving
                // us room to read more data.
                if (markpos > 0) {
                    // this is the size of the data we need to keep.
                    int validSize = pos - markpos;
                    // perform the shift operation.
                    System.arraycopy(buf, markpos, buf, 0, validSize);
                    // now adjust the positional markers for this shift.
                    pos = validSize;
                    bufpos += markpos;
                    markpos = 0;
                }
                // the mark is at the beginning, and we've used up the buffer.  See if we're allowed to
                // extend this.
                else if (buf.length < marklimit) {
                    // try to double this, but throttle to the mark limit
                    int newSize = Math.min(buf.length * 2, marklimit);

                    byte[] newBuffer = new byte[newSize];
                    System.arraycopy(buf, 0, newBuffer, 0, buf.length);

                    // replace the old buffer.  Note that all other positional markers remain the same here.
                    buf = newBuffer;
                }
                // we've got further than allowed, so invalidate the mark, and just reset the buffer
                else {
                    markpos = -1;
                    pos = 0;
                    bufpos += count;
                }
            }
        }

        // if we're past our designated end, force an eof.
        if (bufpos + pos >= start + datalen) {
            // make sure we zero the count out, otherwise we'll reuse this data 
            // if called again. 
            count = pos; 
            return false;
        }

        // seek to the read location start.  Note this is a shared file, so this assumes all of the methods
        // doing buffer fills will be synchronized.
        int fillLength = buf.length - pos;

        // we might be working with a subset of the file data, so normal eof processing might not apply.
        // we need to limit how much we read to the data length.
        if (bufpos - start + pos + fillLength > datalen) {
            fillLength = (int)(datalen - (bufpos - start + pos));
        }

        // finally, try to read more data into the buffer.
        fillLength = source.read(bufpos + pos, buf, pos, fillLength);

        // we weren't able to read anything, count this as an eof failure.
        if (fillLength <= 0) {
            // make sure we zero the count out, otherwise we'll reuse this data 
            // if called again. 
            count = pos; 
            return false;
        }

        // set the new buffer count
        count = fillLength + pos;

        // we have data in the buffer.
        return true;
    }


    /**
     * Return the number of bytes available for reading without
     * blocking for a long period.
     *
     * @return For this stream, this is the number of bytes between the
     *         current read position and the indicated end of the file.
     * @exception IOException
     */
    public synchronized int available() throws IOException {
        checkOpen();

        // this is backed by a file, which doesn't really block.  We can return all the way to the
        // marked data end, if necessary
        long endMarker = start + datalen;
        return (int)(endMarker - (bufpos + pos));
    }


    /**
     * Return the current read position of the stream.
     *
     * @return The current position relative to the beginning of the stream.
     *         This is not the position relative to the start of the file, since
     *         the stream starting position may be other than the beginning.
     */
    public long getPosition() {
        checkOpenRuntime();

        return bufpos + pos - start;
    }


    /**
     * Mark the current position for retracing.
     *
     * @param readlimit The limit for the distance the read position can move from
     *                  the mark position before the mark is reset.
     */
    public synchronized void mark(int readlimit) {
        checkOpenRuntime();
        marklimit = readlimit;
        markpos = pos;
    }


    /**
     * Read a single byte of data from the input stream.
     *
     * @return The read byte.  Returns -1 if an eof condition has been hit.
     * @exception IOException
     */
    public synchronized int read() throws IOException {
        checkOpen();

        // check to see if we can fill more data
        if (!checkFill()) {
            return -1;
        }

        // return the current byte...anded to prevent sign extension.
        return buf[pos++] & 0xff;
    }


    /**
     * Read multiple bytes of data and place them directly into
     * a byte-array buffer.
     *
     * @param buffer The target buffer.
     * @param offset The offset within the buffer to place the data.
     * @param length The length to attempt to read.
     *
     * @return The number of bytes actually read.  Returns -1 for an EOF
     *         condition.
     * @exception IOException
     */
    public synchronized int read(byte buffer[], int offset, int length) throws IOException {
        checkOpen();

        // asked to read nothing?  That's what we'll do.
        if (length == 0) {
            return 0;
        }


        int returnCount = 0;
        while (length > 0) {
            // check to see if we can/must fill more data
            if (!checkFill()) {
                // we've hit the end, but if we've read data, then return that.
                if (returnCount > 0) {
                    return returnCount;
                }
                // trun eof.
                return -1;
            }

            int available = count - pos;
            int given = Math.min(available, length);

            System.arraycopy(buf, pos, buffer, offset, given);

            // now adjust all of our positions and counters
            pos += given;
            length -= given;
            returnCount += given;
            offset += given;
        }
        // return the accumulated count.
        return returnCount;
    }


    /**
     * Skip the read pointer ahead a given number of bytes.
     *
     * @param n      The number of bytes to skip.
     *
     * @return The number of bytes actually skipped.
     * @exception IOException
     */
    public synchronized long skip(long n) throws IOException {
        checkOpen();

        // nothing to skip, so don't skip
        if (n <= 0) {
            return 0;
        }

        // see if we need to fill more data, and potentially shift the mark positions
        if (!checkFill()) {
            return 0;
        }

        long available = count - pos;

        // the skipped contract allows skipping within the current buffer bounds, so cap it there.
        long skipped = available < n ? available : n;
        pos += skipped;
        return skipped;
    }

    /**
     * Reset the mark position.
     *
     * @exception IOException
     */
    public synchronized void reset() throws IOException {
        checkOpen();
        if (markpos < 0) {
            throw new IOException("Resetting to invalid mark position");
        }
        // if we have a markpos, it will still be in the buffer bounds.
        pos = markpos;
    }


    /**
     * Indicates the mark() operation is supported.
     *
     * @return Always returns true.
     */
    public boolean markSupported() {
        return true;
    }


    /**
     * Close the stream.  This does not close the source file until
     * the last shared instance is closed.
     *
     * @exception IOException
     */
    public void close() throws IOException {
        // already closed?  This is not an error
        if (in == null) {
            return;
        }

        try {
            // perform a close on the source version.
            source.close();
        } finally {
            in = null;
        }
    }


    /**
     * Create a new stream from this stream, using the given
     * start offset and length.
     *
     * @param offset The offset relative to the start of this stream instance.
     * @param end    The end offset of the substream.  If -1, the end of the parent stream is used.
     *
     * @return A new SharedFileInputStream object sharing the same source
     *         input file.
     */
    public InputStream newStream(long offset, long end) {
        checkOpenRuntime();

        if (offset < 0) {
            throw new IllegalArgumentException("Start position is less than 0");
        }
        // the default end position is the datalen of the one we're spawning from.
        if (end == -1) {
            end = datalen;
        }

        // create a new one using the private constructor
        return new SharedFileInputStream(source, start + (int)offset, (int)(end - offset), bufsize);
    }


    /**
     * Check if the file is open and throw an IOException if not.
     *
     * @exception IOException
     */
    private void checkOpen() throws IOException {
        if (in == null) {
            throw new IOException("Stream has been closed");
        }
    }


    /**
     * Check if the file is open and throw an IOException if not.  This version is
     * used because several API methods are not defined as throwing IOException, so
     * checkOpen() can't be used.  The Sun implementation just throws RuntimeExceptions
     * in those methods, hence 2 versions.
     *
     * @exception RuntimeException
     */
    private void checkOpenRuntime() {
        if (in == null) {
            throw new RuntimeException("Stream has been closed");
        }
    }


    /**
     * Internal class used to manage resources shared between the
     * ShareFileInputStream instances.
     */
    class SharedFileSource {
        // the file source
        public RandomAccessFile source;
        // the shared instance count for this file (open instances)
        public int instanceCount = 0;

        public SharedFileSource(File file) throws IOException {
            source = new RandomAccessFile(file, "r");
        }

        /**
         * Open the shared stream to keep track of open instances.
         */
        public synchronized RandomAccessFile open() {
            instanceCount++;
            return source;
        }

        /**
         * Process a close request for this stream.  If there are multiple
         * instances using this underlying stream, the stream will not
         * be closed.
         *
         * @exception IOException
         */
        public synchronized void close() throws IOException {
            if (instanceCount > 0) {
                instanceCount--;
                // if the last open instance, close the real source file.
                if (instanceCount == 0) {
                    source.close();
                }
            }
        }

        /**
         * Read a buffer of data from the shared file.
         *
         * @param position The position to read from.
         * @param buf      The target buffer for storing the read data.
         * @param offset   The starting offset within the buffer.
         * @param length   The length to attempt to read.
         *
         * @return The number of bytes actually read.
         * @exception IOException
         */
        public synchronized int read(long position, byte[] buf, int offset, int length) throws IOException {
            // seek to the read location start.  Note this is a shared file, so this assumes all of the methods
            // doing buffer fills will be synchronized.
            source.seek(position);
            return source.read(buf, offset, length);
        }


        /**
         * Ensure the stream is closed when this shared object is finalized.
         *
         * @exception Throwable
         */
        protected void finalize() throws Throwable {
            super.finalize();
            if (instanceCount > 0) {
                source.close();
            }
        }
    }
}

