package com.wqg.com.usbhost.myusblibrary;

import java.util.ArrayList;

public class Analysis {
    Decompose decompose;
    Combination combination;
    Controls controls;
    public Analysis(String text){
        decompose=new Decompose(text);
        Out();
    }
    public int getCount(){
        return combination.size();
    }
    public int getCount(Phase phase){
        return find(phase).size();
    }
    public ArrayList<Package>find(Phase phase){
        ArrayList<Package>arrayList=new ArrayList<>();
        for (Package pack:combination){
            if (pack.phase==phase)arrayList.add(pack);
        }
        return arrayList;
    }
    public void  Out(){
        combination=new Combination(decompose.data);
        controls=new Controls(combination);
    /*    for (Controls.Control control:controls){
            System.out.println(control.CTL.phase +":"+printHexString(control.CTL.data));
            if (control.data!=null)
                System.out.println(control.data.phase +":"+printHexString(control.data.data));
        }*/
    }
    class Controls extends ArrayList<Controls.Control> {
        Controls(Combination combination){
            for (int i=0;i<combination.size();i++){
                Package pack=combination.get(i);

                if (pack.phase== Phase.CTL){
                    Control control=new Control();
                    control.CTL=pack;
                    if (i<combination.size()-1){
                        Package NextPack=combination.get(i+1);
                        if (NextPack.phase== Phase.IN||NextPack.phase== Phase.OUT){
                            int l=(control.CTL.data[6]&0xff)+((control.CTL.data[7]&0xff)<<8);
                            if (l==NextPack.data.length){
                                control.data=NextPack;
                            }
                        }
                    }
                    add(control);
                }
            }
        }
        class Control{
            Control(){}
            Package CTL;
            Package data;

            public byte[] getBytes() {
                if (data!=null){
                    byte[]bytes=new byte[CTL.data.length+data.data.length];
                    System.arraycopy(CTL.data,0,bytes,0,CTL.data.length);
                    System.arraycopy(data.data,0,bytes,CTL.data.length,data.data.length);
                    return bytes;
                }else {
                    return CTL.data;
                }
            }
        }
    }
    class Combination extends ArrayList<Package>{
        ArrayList<Package>ISOC=new ArrayList<>();
        Combination(Info info){
            boolean s;
            Phase phase=null;
            ArrayList<Byte>arrayList=new ArrayList<>();
            for (Package pack:info){
                boolean bi=pack.phase== Phase.IN||pack.phase== Phase.OUT||pack.phase== Phase.ISOC;
                boolean bi2=pack.phase== Phase.CTL||pack.phase== Phase.USTS;
                if (phase!=null&&pack.phase!= Phase.NOT){
                    byte[]bytes=new byte[arrayList.size()];
                    for (int i=0;i<bytes.length;i++){
                        bytes[i]=arrayList.get(i);
                    }
                  Package p=new Package(phase,bytes,null);
                    if (phase== Phase.ISOC){
                        ISOC.add(p);
                    }else {
                        add(p);
                    }
                    arrayList=new ArrayList<>();
                }
                if (bi2){
                    add(pack);
                }
                if (bi){
                    phase=pack.phase;
                }else if (pack.phase!= Phase.NOT){
                    phase=null;
                }
                s =pack.phase== Phase.IN||pack.phase== Phase.OUT||pack.phase== Phase.ISOC||pack.phase== Phase.NOT;
                if (s){
                    if (pack.data!=null){
                        for (int i=0;i<pack.data.length;i++){
                            arrayList.add(pack.data[i]);
                        }
                    }

                }
            }

        }
    }
    class Decompose{
        Decompose(String text){
            String[] strings=text.split("------  -----  ------------------------  ----------------  ------------------");
            if (strings.length==2){
                head=strings[0];
                data=new Info(strings[1]);
            }else {
                System.out.println("err:"+"strings.length="+strings.length);
            }
        }
        String head;
        Info data;
    }

    class Info extends ArrayList<Package>{
        Info(String text){
            String[]strings=text.split("[\n]");
            for (String string:strings){
                if (string.length()>13+26)
                    add(new Package(string));
            }
        }

    }
    class Package{
        Package(String text){
            String D=text.substring(15,39);
            String P=text.substring(8,12).replace(" ","");
            switch (P){
                case "CTL":{
                    phase= Phase.CTL;
                }break;
                case "USTS":{
                    phase= Phase.USTS;
                }break;
                case "ISOC":{
                    phase= Phase.ISOC;
                }break;
                case "IN":{
                    phase= Phase.IN;
                }break;
                case "OUT":{
                    phase= Phase.OUT;
                }break;
                case "":{
                    phase= Phase.NOT;
                }break;
            }
            Description=text.substring(41,64).replace(" ","");
            data =hexStringToBytes(D);
        }
        Package( Phase phase,
                byte[] data,
                String Description){
            this.phase=phase;
            this.data=data;
            this.Description=Description;

        }
        Phase phase;
        byte[] data;
        String Description;
    }
    public enum Phase{
        CTL,
        USTS,
        ISOC,
        IN,
        OUT,
        NOT
    }
    public   String printHexString( byte[] b) {
        if (b==null)return null;
        String a = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }

            a = a+" "+hex;
        }

        return a;
    }
    public static byte[] hexStringToBytes(String hexString) {
        hexString= hexString.replace(" ","");
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));

        }
        return d;
    }
    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }
}
