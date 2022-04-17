import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println("请输入服务器地址：");
        String ipAddr = in.nextLine();
        try {

            Socket socket = new Socket(ipAddr, 10002);
            try (DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                 DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())){
                System.out.println("\n-----------客户端启动-----------");
                while (true) {
                    System.out.println("菜单");
                    System.out.println("1.注册\t2.登录\t3.退出");
                    System.out.println("请输入序号：");
                    int num = in.nextInt();
                    in.nextLine();
                    if (num != 1 && num != 2 && num != 3) {
                        System.out.println("输入有误，请重新输入");
                        continue;
                    }
                    if (num == 3) {
                        System.out.println("正在退出客户端");
                        break;
                    }
                    if(num == 1){
                        System.out.println("请输入注册账户：");
                        String account = in.nextLine();
                        System.out.println("请输入密码：");
                        String password = in.nextLine();
                        outputStream.writeInt(num);
                        outputStream.writeUTF(account);
                        outputStream.writeUTF(password);
                        boolean result = inputStream.readBoolean();
                        if(result)
                            System.out.println("注册成功\n");
                        else
                            System.out.println("注册失败，已存在该账户\n");

                    } else if(num == 2) {
                        boolean result = login(in, num, inputStream, outputStream);
                        if(!result){
                            System.out.println("登录失败，账户或密码错误\n");
                            continue;
                        }
                        System.out.println("登录成功");
                        while (true){
                            System.out.println("\n1.上传文件\t2.下载文件\t3.浏览文件\t4.返回上一级");
                            System.out.println("请输入序号：");
                            int num1 = in.nextInt();
                            in.nextLine();
                            if (num1 !=1 && num1 !=2 && num1 !=3 && num1 != 4) {
                                System.out.println("输入序号有误");
                                continue;
                            }

                            if (num1 == 1) {
                                upFile(in, num1, inputStream, outputStream);
                            }
                            else if (num1 == 2) {
                                downFile(in, num1, inputStream, outputStream);
                            }
                            else if (num1 == 3) {
                                outputStream.writeInt(num1 + 2);
                                listFile(inputStream);
                            }
                            else if (num1 == 4) {
                                logout(num1, inputStream, outputStream);
                                break;
                            }

                        }

                    }
                }
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            in.close();
        }
    }
    private static boolean login(Scanner in, int num, DataInputStream inputStream, DataOutputStream outputStream) throws IOException{
        System.out.println("请输入账户：");
        String account = in.nextLine();
        System.out.println("请输入密码：");
        String password = in.nextLine();
        outputStream.writeInt(num);
        outputStream.writeUTF(account);
        outputStream.writeUTF(password);
        boolean result = inputStream.readBoolean();
        if(!result){
            return false;
        }
        return true;
    }
    private static boolean upFile(Scanner in, int num, DataInputStream inputStream, DataOutputStream outputStream) throws IOException{
        System.out.println("请输入上传文件路径：");
        String filepathstr = in.nextLine();
        Path filepath = Paths.get(filepathstr);
        if (!Files.exists(filepath) || Files.isDirectory(filepath)) {
            System.out.println("输入文件不存在或者不是文件");
            return false;
        }
        DataInputStream fileInputStream = new DataInputStream(Files.newInputStream(filepath));
        outputStream.writeInt(num+2);
        outputStream.writeUTF(filepath.getFileName().toString());
        byte[] buffer = new byte[4096];
        while (fileInputStream.read(buffer) != -1) {
            outputStream.write(buffer);
        }
        byte[] EOF = "文件结束".getBytes(StandardCharsets.UTF_8);
        byte[] sended = new byte[4096];
        for (int i = 0; i < EOF.length; i++) {
            sended[i] = EOF[i];
        }
        outputStream.write(sended);
        boolean result = inputStream.readBoolean();
        if (result){
            System.out.println("文件上传完成");
        } else {
            System.out.println("文件上传失败");
        }
        return result;
    }
    private static boolean downFile(Scanner in, int num, DataInputStream inputStream, DataOutputStream outputStream
                                    ) throws IOException{
        //告知服务器要下载文件
        outputStream.writeInt(num + 2);
        ArrayList<String> filenames = listFile(inputStream);
        if(filenames == null)
            return false;
        System.out.println("输入文件名前的序号来下载文件：");
        int fileindex = in.nextInt();
        in.nextLine();
        System.out.println("请输入文件存放地址：");
        String filedir = in.nextLine();
        Path filepath = Paths.get(filedir, filenames.get(fileindex-1));
        if(!Files.exists(filepath.getParent())){
            System.out.println("该路径不存在，请输入已存在路径");
            return false;
        }
        //结束标识
        byte[] EOF = "文件结束".getBytes(StandardCharsets.UTF_8);
        byte[] received = new byte[4096];
        for (int i = 0; i < EOF.length; i++) {
            received[i] = EOF[i];
        }

        //告知服务器文件名
        outputStream.writeInt(fileindex);
        //从服务器端获取是否存在该文件
        boolean fileExisted = inputStream.readBoolean();
        if(!fileExisted){
            System.out.println("下载的文件不存在");
            return false;
        }
        try(OutputStream fileOutStream = Files.newOutputStream(filepath)) {
            byte[] buffer = new byte[4096];
            int available = inputStream.available();
            while (inputStream.read(buffer) != -1 && !new String(buffer, "utf-8")
                    .equals(new String(received, "utf-8"))) {
                if (available > 4096)
                    fileOutStream.write(buffer);
                else
                    fileOutStream.write(buffer, 0, available);
                available = inputStream.available();
            }
            outputStream.writeBoolean(true);
            System.out.println(filenames.get(fileindex-1) + "文件下载完成\n");
            return true;
        }
    }

    private static ArrayList<String> listFile(DataInputStream inputStream) throws IOException {

        boolean result = inputStream.readBoolean();
        if (!result) {
            System.out.println("无文件");
            return null;
        }
        //文件数量
        int size = inputStream.readInt();
        System.out.println("共包含" + size + "个文件");
        String filenamestr;
        ArrayList<String> filenames = new ArrayList<>();
        while (!(filenamestr = inputStream.readUTF()).equals("END")) {
            System.out.println(filenamestr);
            filenames.add(filenamestr.substring(filenamestr.indexOf(".")+1));
        }
        return filenames;
    }

    private static void logout(int num, DataInputStream inputStream, DataOutputStream outputStream) throws IOException{
        outputStream.writeInt(num + 2);
        boolean result = inputStream.readBoolean();
        if (result)
            System.out.println("注销成功");
        else
            System.out.println("注销失败");
    }
}
