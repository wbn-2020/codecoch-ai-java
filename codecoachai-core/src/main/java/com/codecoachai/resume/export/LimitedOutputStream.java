package com.codecoachai.resume.export;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LimitedOutputStream extends FilterOutputStream {

    private final long limit;
    private long count;

    public LimitedOutputStream(OutputStream output, long limit) {
        super(output);
        this.limit = limit;
    }

    @Override
    public void write(int value) throws IOException {
        ensureCapacity(1);
        out.write(value);
        count++;
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        ensureCapacity(length);
        out.write(buffer, offset, length);
        count += length;
    }

    public long count() {
        return count;
    }

    private void ensureCapacity(int nextBytes) throws IOException {
        if (nextBytes < 0 || count + nextBytes > limit) {
            throw new IOException("Artifact exceeds configured size limit");
        }
    }
}
