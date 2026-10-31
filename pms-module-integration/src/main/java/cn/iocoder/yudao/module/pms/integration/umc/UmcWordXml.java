package cn.iocoder.yudao.module.pms.integration.umc;

final class UmcWordXml {
    private UmcWordXml() { }
    static void validate(byte[] bytes) throws Exception {
        var factory = javax.xml.parsers.SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        reader.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException {
                throw new org.xml.sax.SAXException("Invalid WordML");
            }
            @Override public void fatalError(org.xml.sax.SAXParseException error) throws org.xml.sax.SAXException {
                throw new org.xml.sax.SAXException("Invalid WordML");
            }
        });
        reader.setContentHandler(new org.xml.sax.helpers.DefaultHandler() {
            private boolean root;
            @Override public void startElement(String uri, String local, String qualified, org.xml.sax.Attributes attributes)
                    throws org.xml.sax.SAXException {
                if (!root && (!"wordDocument".equals(local)
                        || !"http://schemas.microsoft.com/office/word/2003/wordml".equals(uri)))
                    throw new org.xml.sax.SAXException("Invalid WordML root");
                root = true;
            }
        });
        reader.parse(new org.xml.sax.InputSource(new java.io.ByteArrayInputStream(bytes)));
    }
}
