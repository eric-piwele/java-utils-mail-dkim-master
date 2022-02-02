/* 
 * Copyright 2008 The Apache Software Foundation or its licensors, as
 * applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * A licence was granted to the ASF by Florian Sager on 30 November 2008
 */
package net.markenwerk.utils.mail.dkim;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import com.sun.mail.smtp.SMTPMessage;

import net.markenwerk.utils.data.fetcher.BufferedDataFetcher;

/**
 * Extension of {@link SMTPMessage} for the inclusion of a DKIM signature.
 * 
 * @author Torsten Krause (tk at markenwerk dot net)
 * @author Florian Sager
 * @since 1.0.0
 */
public class DkimMessage extends SMTPMessage {

	private static byte[] NL = { (byte) '\r', (byte) '\n' };

	private DkimSigner signer;

	private String encodedBody;

	/**
	 * Created a new {@code DkimMessage} from the given {@link MimeMessage} and
	 * {@link DkimSigner}.
	 * 
	 * @param message
	 *            The {@link MimeMessage} to be signed.
	 * @param signer
	 *            The {@link DkimSigner} to sign the message with.
	 * @throws MessagingException
	 *             If constructing this {@code DkimMessage} failed.
	 */
	public DkimMessage(MimeMessage message, DkimSigner signer) throws MessagingException {
		super(message);
		this.signer = signer;
	}

	/**
	 * Output the message as an RFC 822 format stream, without specified
	 * headers. If the <code>saved</code> flag is not set, the
	 * <code>saveChanges</code> method is called. If the <code>modified</code>
	 * flag is not set and the <code>content</code> array is not null, the
	 * <code>content</code> array is written directly, after writing the
	 * appropriate message headers.
	 *
	 * This method enhances the JavaMail method
	 * {@link MimeMessage#writeTo(OutputStream, String[])} See the according Sun
	 * license, this contribution is CDDL.
	 * 
	 * @exception MessagingException
	 *                If an error occurs while preparing this message for
	 *                writing.
	 * @exception IOException
	 *                If an error occurs while writing to the stream or if an
	 *                error is generated by the javax.activation layer.
	 */
	@Override
	public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {

		// inside saveChanges it is assured that content encodings are set in
		// all parts of the body
		if (!saved) {
			saveChanges();
		}

		ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
		if (modified) {
			// write out the body from the dataHandler through the
			// encodingOutputStream into the bodyBuffer
			OutputStream encodingOutputStream = MimeUtility.encode(bodyBuffer, getEncoding());
			getDataHandler().writeTo(encodingOutputStream);
			encodingOutputStream.flush();
			encodingOutputStream.close();
		} else if (null == content) {
			// write the provided contentStream into the bodyBuffer
			new BufferedDataFetcher().copy(getContentStream(), bodyBuffer, true, false);
			bodyBuffer.flush();
			bodyBuffer.close();
		} else {
			// just write the readily available content into the bodyBuffer
			bodyBuffer.write(content);
			bodyBuffer.flush();
			bodyBuffer.close();
		}

		encodedBody = bodyBuffer.toString();

		// second, sign the message
		String signatureHeaderLine = signer.sign(this);

		// write the 'DKIM-Signature' header, all other headers and a clear \r\n
		writeln(os, signatureHeaderLine);
		Enumeration<String> headerLines = getNonMatchingHeaderLines(ignoreList);
		while (headerLines.hasMoreElements()) {
			writeln(os, headerLines.nextElement());
		}
		writeln(os);
		os.flush();

		// write the message body
		os.write(bodyBuffer.toByteArray());
		os.flush();
	}

	/**
	 * Returns the encoded body.
	 * 
	 * @return The encoded body.
	 */
	protected String getEncodedBody() {
		return encodedBody;
	}

	@Override
	public void setAllow8bitMIME(boolean allow) {
		// don't allow to switch to 8-bit MIME, instead 7-bit ASCII should be
		// kept because in forwarding scenarios a change to
		// Content-Transfer-Encoding to 7-bit ASCII breaks the DKIM signature
		super.setAllow8bitMIME(false);
	}

	private static void writeln(OutputStream out) throws IOException {
		out.write(NL);
	}

	private static void writeln(OutputStream out, String string) throws IOException {
		byte[] bytes = getBytes(string);
		out.write(bytes);
		out.write(NL);
	}

	private static byte[] getBytes(String string) {
		char[] chars = string.toCharArray();
		byte[] bytes = new byte[chars.length];

		for (int i = 0, n = chars.length; i < n; i++) {
			bytes[i] = (byte) chars[i];
		}

		return bytes;
	}

}
