package com.cybermkd.upload.multipart;

import com.cybermkd.common.http.exception.WebException;
import com.cybermkd.common.util.stream.FileRenamer;

import java.io.*;

/**
 * A <code>FilePart</code> is an upload part which represents a
 * <code>INPUT TYPE="file"</code> form parameter.  Note that because file
 * upload data arrives via a single InputStream, each FilePart's contents
 * must be read before moving onto the next part.  Don't try to store a
 * FilePart object for later processing because by then their content will
 * have been passed by.
 *
 * @author Geoff Soutter
 * @version 1.0, 2000/10/27, initial revision
 */
public class FilePart extends Part {

    private File dir;
    /**
     * "file system" name of the file
     */
    private String fileName;

    /**
     * path of the file as sent in the request, if given
     */
    private String filePath;

    /**
     * content type of the file
     */
    private String contentType;

    /**
     * input stream containing file data
     */
    private PartInputStream partInput;

    /**
     * file rename renamer
     */
    private FileRenamer renamer;

    /**
     * Construct a file part; this is called by the parser.
     *
     * @param name        the name of the parameter.
     * @param in          the servlet input stream to read the file from.
     * @param boundary    the MIME boundary that delimits the end of file.
     * @param contentType the content type of the file provided in the
     *                    MIME header.
     * @param fileName    the file system name of the file provided in the
     *                    MIME header.
     * @param filePath    the file system path of the file provided in the
     *                    MIME header (as specified in disposition info).
     * @throws IOException if an input or output exception has occurred.
     */
    FilePart(String name, InputStream in, String boundary,
             String contentType, String fileName, String filePath)
            throws IOException {
        super(name);
        this.fileName = fileName;
        this.filePath = filePath;
        this.contentType = contentType;
        partInput = new PartInputStream(in, boundary);
    }

    /**
     * Puts in place the specified renamer for handling file name collisions.
     */
    public void setRenamer(FileRenamer renamer) {
        this.renamer = renamer;
    }

    public File getDir() {
        return dir;
    }

    /**
     * Returns the name that the file was stored with on the remote system,
     * or <code>null</code> if the user didn't enter a file to be uploaded.
     * Note: this is not the same as the name of the form parameter used to
     * transmit the file; that is available from the <code>getName</code>
     * method.  Further note: if file rename logic is in effect, the file
     * name can change during the writeTo() method when there's a collision
     * with another file of the same name in the same directory.  If this
     * matters to you, be sure to pay attention to when you call the method.
     *
     * @return name of file uploaded or <code>null</code>.
     * @see Part#getName()
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Returns the full path and name of the file on the remote system,
     * or <code>null</code> if the user didn't enter a file to be uploaded.
     * If path information was not supplied by the remote system, this method
     * will return the same as <code>getFileName()</code>.
     *
     * @return path of file uploaded or <code>null</code>.
     * @see Part#getName()
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Returns the content type of the file data contained within.
     *
     * @return content type of the file data.
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Returns an input stream which contains the contents of the
     * file supplied. If the user didn't enter a file to upload
     * there will be <code>0</code> bytes in the input stream.
     * It's important to read the contents of the InputStream
     * immediately and in full before proceeding to process the
     * next part.  The contents will otherwise be lost on moving
     * to the next part.
     *
     * @return an input stream containing contents of file.
     */
    public InputStream getInputStream() {
        return partInput;
    }

    /**
     * Write this file part to a file or directory. If the user
     * supplied a file, we write it to that file, and if they supplied
     * a directory, we write it to that directory with the filename
     * that accompanied it. If this part doesn't contain a file this
     * method does nothing.
     *
     * @return number of bytes written
     * @throws IOException if an input or output exception has occurred.
     */
    public long writeTo(File fileOrDirectory) throws IOException {
        long written = 0;

        OutputStream fileOut = null;
        try {
            // Only do something if this part contains a file
            if (fileName != null) {
                // Check if user supplied directory
                File file;
                if (fileOrDirectory.isDirectory()) {
                    // Write it to that dir the user supplied,
                    // with the filename it arrived with
                    file = new File(fileOrDirectory, fileName);
                } else {
                    // Write it to the file the user supplied,
                    // ignoring the filename it arrived with
                    file = fileOrDirectory;
                }
                if (renamer != null) {
                    file = renamer.rename(file);
                    fileName = file.getName();
                }
                //create parent dir
                File parent = file.getParentFile();
                if (!parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw new WebException("Directory " + parent + " not exists and can not create directory.");
                    }
                }
                dir = parent;
                fileOut = new BufferedOutputStream(new FileOutputStream(file));
                written = write(fileOut);
            }
        } finally {
            if (fileOut != null) fileOut.close();
        }
        return written;
    }

    /**
     * Write this file part to the given output stream. If this part doesn't
     * contain a file this method does nothing.
     *
     * @return number of bytes written.
     * @throws IOException if an input or output exception has occurred.
     */
    public long writeTo(OutputStream out) throws IOException {
        long size = 0;
        // Only do something if this part contains a file
        if (fileName != null) {
            // Write it out
            size = write(out);
        }
        return size;
    }

    /**
     * Internal method to write this file part; doesn't check to see
     * if it has contents first.
     *
     * @return number of bytes written.
     * @throws IOException if an input or output exception has occurred.
     */
    long write(OutputStream out) throws IOException {
        // decode macbinary if this was sent
        if (contentType.equals("application/x-macbinary")) {
            out = new MacBinaryDecoderOutputStream(out);
        }
        long size = 0;
        int read;
        byte[] buf = new byte[8 * 1024];
        while ((read = partInput.read(buf)) != -1) {
            out.write(buf, 0, read);
            size += read;
        }
        return size;
    }

    /**
     * Returns <code>true</code> to indicate this part is a file.
     *
     * @return true.
     */
    public boolean isFile() {
        return true;
    }
}
