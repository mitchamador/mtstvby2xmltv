package by.mitchamador.xmltv;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class XMLTV {

    private String filename;
    //@SerializedName("channel")
    private List<Channel> channels = new ArrayList<Channel>();
    //@SerializedName("programme")
    private List<Programme> programmes = new ArrayList<Programme>();

    public void setChannelsOnly(boolean channelsOnly) {
        this.channelsOnly = channelsOnly;
    }

    private boolean channelsOnly;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public List<Programme> getProgrammes() {
        return programmes;
    }

    public static final String DATE_FORMAT_STRING = "yyyyMMddHHmmss Z";
    public static DateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING);

    public static String getAttr(StartElement start, String attr) {
        for (Iterator<Attribute> it = start.getAttributes(); it.hasNext(); ) {
            Attribute a = it.next();
            if (a.getName().getLocalPart().equals(attr)) {
                return a.getValue();
            }
        }
        return null;
    }

    public static int getAttrIntValue(StartElement start, String attr) {
        for (Iterator<Attribute> it = start.getAttributes(); it.hasNext(); ) {
            Attribute a = it.next();
            if (a.getName().getLocalPart().equals(attr)) {
                try {
                    return Integer.parseInt(a.getValue());
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }

    public static String escapeXml(String st, boolean attribute) {
        if (st == null) return "";
        //st = st.replaceAll("[^\\u0020-\\uD7FF\\uE000-\\uFFFD\\t\\n\\r]", " ");

        st = st.replace("&", "&amp;");
        st = st.replace(">", "&gt;");
        st = st.replace("<", "&lt;");

        if (attribute) {
            st = st.replace("'", "&apos;");
            st = st.replace("\"", "&quot;");
        }

        return st;
    }

    static String getIndent(int level) {
        StringBuilder s = new StringBuilder(128);
        for (int i = 0; i < level; i++) {
            s.append('\t');
        }
        return s.toString();
    }

    public XMLTV() {
    }

    public void parseStax(String filename) throws IOException {

        this.filename = filename;

        InputStream in;
        URLConnection con = null;
        if (filename.startsWith("http://") || filename.startsWith("https://")) {
            URL url = new URL(filename);
            con = url.openConnection();
            in = new BufferedInputStream(con.getInputStream());
        } else {
            in = new BufferedInputStream(new FileInputStream(filename));
        }

        if (filename.endsWith(".gz")) {
            in = new GZIPInputStream(con == null ? in : con.getInputStream());
        }

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, (c.get(Calendar.HOUR_OF_DAY) / 6) * 6);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        long begin = c.getTime().getTime();
        c.add(Calendar.HOUR_OF_DAY, 3 * 24);
        long end = c.getTime().getTime();

        try {
            XMLEventReader xmlEventReader = xmlInputFactory.createXMLEventReader(in);

            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.nextEvent();
                if (xmlEvent.isStartElement()) {
                    StartElement start = xmlEvent.asStartElement();
                    String name = start.getName().getLocalPart();
                    if (name.equals("channel")) {
                        channels.add(new Channel(xmlEventReader, start));
                    } else if (!channelsOnly && name.equals("programme")) {
                        try {
                            Programme p = new Programme(xmlEventReader, start);
                            if (p.getStart() != null && p.getStop() != null
                                    && ((p.getStart().getTime() >= begin && p.getStart().getTime() <= end) || (p.getStop().getTime() >= begin && p.getStop().getTime() <= end))
                            ) {
                                programmes.add(p);
                            }
                        } catch (java.text.ParseException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
    }

    public String toXML() {
        StringBuilder s = new StringBuilder(65536);

        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        s.append("<tv>\n");

        if (channels != null) {
            for (Channel channel : channels) {
                s.append(channel.toXml(1));
            }
        }

        if (programmes != null) {
            for (Programme programme : programmes) {
                s.append(programme.toXml(1));
            }
        }

        s.append("</tv>\n");

        return s.toString();
    }

}