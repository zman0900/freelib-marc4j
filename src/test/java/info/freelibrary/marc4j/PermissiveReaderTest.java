
package info.freelibrary.marc4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import org.junit.Test;
import org.marc4j.MarcPermissiveStreamReader;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;

import info.freelibrary.marc4j.utils.RecordTestingUtils;
import info.freelibrary.marc4j.utils.StaticTestRecords;

public class PermissiveReaderTest {

    @Test
    public void testBadLeaderBytes10_11() throws Exception {
        int i = 0;
        final InputStream input = getClass().getResourceAsStream(StaticTestRecords.RESOURCES_BAD_LEADERS_10_11_MRC);
        assertNotNull(input);
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true);
        while (reader.hasNext()) {
            final Record record = reader.next();

            assertEquals(2, record.getLeader().getIndicatorCount());
            assertEquals(2, record.getLeader().getSubfieldCodeLength());
            i++;
        }
        input.close();
        assertEquals(1, i);
    }

    @Test
    public void testNumericCodeEscapingEnabled() throws Exception {
        final ByteArrayInputStream in = getInputStreamForTestRecordWithNumericCoding();
        final MarcPermissiveStreamReader reader = new MarcPermissiveStreamReader(in, false, true, "MARC-8");
        assertEquals("default lossless code expansion", true, reader
                .isTranslateLosslessUnicodeNumericCodeReferencesEnabled());

        assertTrue("have a record", reader.hasNext());
        final Record r = reader.next();
        assertFalse("too many records", reader.hasNext());
        final DataField f = (DataField) r.getVariableField("999");
        final Subfield sf = f.getSubfield('a');
        assertEquals("Should be expanded", "Character Test", sf.getData());
    }

    @Test
    public void testNumericCodeEscapingDisabled() throws Exception {
        final ByteArrayInputStream in = getInputStreamForTestRecordWithNumericCoding();
        final MarcPermissiveStreamReader reader = new MarcPermissiveStreamReader(in, true, true, "MARC-8");
        reader.setTranslateLosslessUnicodeNumericCodeReferencesEnabled(false);
        assertEquals("default lossless code expansion", false, reader
                .isTranslateLosslessUnicodeNumericCodeReferencesEnabled());

        assertTrue("have a record", reader.hasNext());
        final Record r = reader.next();
        assertFalse("too many records", reader.hasNext());
        final DataField f = (DataField) r.getVariableField("999");
        final Subfield sf = f.getSubfield('a');
        assertEquals("Should NOT be expanded", "&#x0043;haracter Test", sf.getData());
    }

    private ByteArrayInputStream getInputStreamForTestRecordWithNumericCoding() {
        final MarcFactory factory = MarcFactory.newInstance();
        final Record r = StaticTestRecords.chabon[0];
        r.getLeader().setCharCodingScheme(' ');
        final VariableField f = factory.newDataField("999", ' ', ' ', "a", "&#x0043;haracter Test");
        r.addVariableField(f);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final MarcStreamWriter writer = new MarcStreamWriter(out);
        writer.write(r);
        writer.close();
        final byte recordBytes[] = out.toByteArray();

        return new ByteArrayInputStream(recordBytes);
    }

    @Test
    public void testTooLongMarcRecord() throws Exception {
        final InputStream input = getClass().getResourceAsStream(StaticTestRecords.RESOURCES_BAD_TOO_LONG_PLUS_2_MRC);
        assertNotNull(input);
        // This MARC file has three records, but the first one
        // is too long for a MARC binary record. Can we still read
        // the next two?
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true);

        final Record bad_record = reader.next();

        // Bad record is a total loss, don't even bother trying to read
        // it, but do we get the good records next?
        final Record good_record1 = reader.next();
        ControlField good001 = good_record1.getControlNumberField();
        assertEquals(good001.getData(), "360945");

        final Record good_record2 = reader.next();
        good001 = good_record2.getControlNumberField();
        assertEquals(good001.getData(), "360946");

    }

    @Test
    public void testTooLongLeaderByteRead() throws Exception {
        final InputStream input = getClass().getResourceAsStream(StaticTestRecords.RESOURCES_BAD_TOO_LONG_PLUS_2_MRC);
        assertNotNull(input);
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true);

        // First record is the long one.
        final Record weird_record = reader.next();

        // is it's marshal'd leader okay?
        final String strLeader = weird_record.getLeader().marshal();

        // Make sure only five digits for length is used in the leader,
        // even though it's not big enough to hold the leader, we need to
        // make sure byte offsets in the rest of the leader are okay.
        assertEquals("nas", strLeader.substring(5, 8));

        // And length should be set to our 99999 overflow value
        assertEquals("99999", strLeader.substring(0, 5));
    }

    // This test is targeted toward code that attempts to fix instances where a vertical bar character
    // has been interpreted as a sub-field separator, specifically in this case when the vertical bar
    // occurs in a string of cyrillic characters, and is supposed to be a CYRILLIC CAPITAL LETTER E
    @Test
    public void testCyrillicEFix() throws Exception {
        final InputStream input = getClass().getResourceAsStream(StaticTestRecords.RESOURCES_CYRILLIC_CAPITAL_E_MRC);
        assertNotNull(input);
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true);

        while (reader.hasNext()) {
            final Record record = reader.next();

            // Get fields with Cyrillic characters
            final List<VariableField> fields = record.getVariableFields("880");

            for (final VariableField field : fields) {
                final DataField df = (DataField) field;
                Subfield sf = df.getSubfield('6');
                if (sf.getData().startsWith("26")) {
                    sf = df.getSubfield('b');
                    // test string is Эксмо,
                    final String testString = "\u042D\u043A\u0441\u043C\u043E,";
                    if (!sf.getData().equalsIgnoreCase(testString)) {
                        fail("broken cyrillic record should have been fixed");
                    }
                }
            }
        }
    }

    // This test is targeted toward code that attempts to fix instances where within a string of characters
    // in the greek character set, a character set change back to the default character set is missing.
    // This would be indicated by characters being found for which there is no defined mapping in the greek character
    // set
    // (typically a punctuation mark) or by an unlikely sequence of punctuation marks being found, which typically
    // would
    // indicate a sequence of numerals. The test reads a marc8 encoded version of a record with greek characters that
    // has
    // been damaged by an ILS system, through having character set changes back to the default character set deleted,
    // followed by that record represented in utf8, such that no character set changes are expected or needed,
    // followed by a
    // third copy of the same record represented in marc8 but using numeric character references to encode the greek
    // characters.
    @Test
    public void testGreekMissingCharSetChange() throws Exception {
        final InputStream input = getClass().getResourceAsStream(
                StaticTestRecords.RESOURCES_GREEK_MISSING_CHARSET_MRC);
        assertNotNull(input);
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true);

        final Record record1 = reader.next();
        final Record record2 = reader.next();
        final Record record3 = reader.next();

        if (record1 != null && record2 != null) {
            RecordTestingUtils.assertEqualsIgnoreLeader(record1, record2);
        }
        if (record2 != null && record3 != null) {
            RecordTestingUtils.assertEqualsIgnoreLeader(record2, record3);
        }
    }

    // This test is targeted toward code that attempts to fix instances where Marc8 multibyte-encoded CJK characters
    // are malformed, due to some software program deleting the characters '[' or ']' or '|'. Each of these can
    // occur as a part of a Marc8 multibyte-encoded CJK characters, but some poorly written software treats the
    // vertical
    // bar characters as subfield separators, and also summarily deletes the square brackets, which damages the Marc8
    // multibyte-encoded CJK characters and makes translating the data to Unicode extremely difficult.
    @Test
    public void testMangledChineseCharacters() throws Exception {
        final InputStream input = getClass().getResourceAsStream(
                StaticTestRecords.RESOURCES_CHINESE_MANGLED_MULTIBYTE_MRC);
        assertNotNull(input);
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true, "MARC8");

        final Record record1 = reader.next();
        final Record record2 = reader.next();
        final Record record3 = reader.next();
        final Record record4 = reader.next();
        // if (record1 != null && record2 != null)
        // RecordTestingUtils.assertEqualsIgnoreLeader(record1, record2);
        final String diff12 = RecordTestingUtils.getFirstRecordDifferenceIgnoreLeader(record1, record2);
        final String diff23 = RecordTestingUtils.getFirstRecordDifferenceIgnoreLeader(record2, record3);
        final String diff34 = RecordTestingUtils.getFirstRecordDifferenceIgnoreLeader(record3, record4);
        assertNull("Tested records are unexpectedly different: " + diff23, diff23);
        assertNull("Tested records are unexpectedly different: " + diff34, diff34);

    }

    // This test is targeted toward code that attempts to fix instances where a Numeric Character Reference
    // is malformed. The NCR somtimes is missing the terminal semicolon, and in other cases encountered in the wild
    // the NCR is like &#x0E01%x; with an extraneous %x inserted in the NCR. The tested code looks for this pattern
    // (when translation of NCR's is enabled) and deletes it before translating the NCR to the specified Unicode code
    // point.
    // The test file consists of two copies of the same record. One contains the malformed NCRs, the other contains
    // the
    // record correctly encoded in Unicode. After translating the records should be identical.
    @Test
    public void testMalformedNCRFix() throws Exception {
        final InputStream input = getClass().getResourceAsStream(
                StaticTestRecords.RESOURCES_BAD_NUMERIC_CHARACTER_REFERENCE_MRC);
        assertNotNull(input);
        final MarcReader reader = new MarcPermissiveStreamReader(input, true, true, "MARC8");

        final Record record1 = reader.next();
        final Record record2 = reader.next();

        final String diff12 = RecordTestingUtils.getFirstRecordDifferenceIgnoreLeader(record1, record2);
        assertNull("Tested records are unexpectedly different: " + diff12, diff12);

    }

}
