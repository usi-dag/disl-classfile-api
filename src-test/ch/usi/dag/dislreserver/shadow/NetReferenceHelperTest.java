package ch.usi.dag.dislreserver.shadow;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.Theories;
import org.junit.runner.RunWith;

@RunWith (Theories.class)
public class NetReferenceHelperTest {

    @Test
    public void testFlagMaskCalculation () {
        // 1-bit fields
        Assert.assertEquals (0x0000000000000001L, NetReferenceHelper.__mask (1, 0));
        Assert.assertEquals (0x0000000080000000L, NetReferenceHelper.__mask (1, 31));
        Assert.assertEquals (0x0000000100000000L, NetReferenceHelper.__mask (1, 32));
        Assert.assertEquals (0x8000000000000000L, NetReferenceHelper.__mask (1, 63));

        // Actual shift amount is the requested amount modulo 64 (shift & 0x3f).
        Assert.assertEquals (0x0000000000000001L, NetReferenceHelper.__mask (1, 64));
    }


    @Test
    public void testBitFieldMaskCalculation () {
        Assert.assertEquals (0x000000ffffffffffL, NetReferenceHelper.__mask (40, 0));
        Assert.assertEquals (0x3fffff0000000000L, NetReferenceHelper.__mask (22, 40));
        Assert.assertEquals (0xc000000000000000L, NetReferenceHelper.__mask (2, 62));
    }

    // TODO Test flag and bit field extraction

}
