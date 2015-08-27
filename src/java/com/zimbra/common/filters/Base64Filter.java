/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2012, 2013, 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.common.filters;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.codec.binary.Base64OutputStream;

public class Base64Filter implements Filter {

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
    throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest)) {
            chain.doFilter(req, resp);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (isEncodeable(request)) {
            Base64ResponseWrapper wrappedResponse = new Base64ResponseWrapper(response);
            chain.doFilter(req, wrappedResponse);
            wrappedResponse.finishResponse();
        } else {
            chain.doFilter(req, resp);
        }
    }

    public static boolean isEncodeable(HttpServletRequest request) {
        String ae = request.getHeader("x-zimbra-encoding");
        return ae != null && (ae.trim().equals("base64") || ae.trim().equals("x-base64"));
    }


    public class Base64ResponseWrapper extends HttpServletResponseWrapper {
        private final HttpServletResponse response;
        private ServletOutputStream output;
        private PrintWriter writer;

        public Base64ResponseWrapper(HttpServletResponse resp) {
            super(resp);
            response = resp;
            response.setHeader("X-Zimbra-Encoding", "x-base64");
        }

        void finishResponse() throws IOException {
            if (writer != null) {
                writer.close();
            } else if (output != null) {
                output.close();
            }
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            } else if (output != null) {
                output.flush();
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (output == null) {
                if (writer != null) {
                    throw new IllegalStateException("getWriter() has already been called!");
                }
                output = new Base64ResponseStream(response);
            }
            return output;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                if (output != null) {
                    throw new IllegalStateException("getOutputStream() has already been called!");
                }
                writer = new PrintWriter(new OutputStreamWriter(new Base64ResponseStream(response), response.getCharacterEncoding()));
            }
            return writer;
        }

        @Override
        public void setContentLength(int length) {
        }
    }

    public static class Base64ResponseStream extends ServletOutputStream {
        protected Base64OutputStream output;

        public Base64ResponseStream(HttpServletResponse resp) throws IOException {
            super();
            output = new Base64OutputStream(resp.getOutputStream());
        }

        @Override
        public void flush() throws IOException {
            output.flush();
        }

        @Override
        public void write(int b) throws IOException {
            output.write(b);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            output.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            output.close();
        }

        @Override
        public boolean isReady() {
            return true;
            //TODO: this isn't right, just stubbed for now so we can build
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            //TODO: this isn't right, just stubbed for now so we can build
        }

    }
}
