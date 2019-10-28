package lab1;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class CommunicateThread extends Thread {

  Socket client_socket;// ��ͻ���ͨ�ŵĴ�����������׽���
  Socket server_socket;// ���������ͨ�ŵĴ�����������׽���
  String request_gram = "";// �������Կͻ��˵�������
  String respose_gram = "";// �������Է���������Ӧ����
  int socket_time_out = 1000;// socket�ĳ�ʱʱ�䣬����Ϊ1000ms
  byte[] respose_byte;// ���ڴ�����ܵ���Ӧ�����ֽ�����Ϣ
  int port = 80;// Ĭ��������������Ӷ˿�Ϊ80
  String URL;// ͷ�����е�URL
  String host;// ͷ�����е�host

  /**
   * to:����client_socket��ͻ��˽�������ͨ��.
   * 
   * @param client_socket ��ͻ���ͨ�ŵ�socket
   */
  public CommunicateThread(Socket client_socket) {
    this.client_socket = client_socket;
  }

  /**
   * in order to:������վ���ˡ��û����ˡ���վ����
   */
  public boolean filter_and_phishing() throws IOException {
    if (!HTTP_Proxy.user_filter && !HTTP_Proxy.web_filter) {
      return true;
    }
    BufferedReader bfr_filter =
        new BufferedReader(new FileReader(HTTP_Proxy.configuraion_file));
    String line_filter = "";
    while ((line_filter = bfr_filter.readLine()) != null) {
      if (HTTP_Proxy.user_filter// ���������û������ҿͻ����û�Ϊ�������û����򷵻�false
          && line_filter.contains(client_socket.getInetAddress().getHostAddress())
          && line_filter.contains("user_filter")) {
        System.out.println("�û�����:\t" + client_socket.getInetAddress().getHostAddress());
        bfr_filter.close();
        return false;
      } else if (HTTP_Proxy.web_filter && line_filter.contains(this.host)
          && line_filter.contains("web_filter")) {// ����������վ������Ŀ������Ϊ�����˵��������򷵻�
        System.out.println("��վ����:\t" + this.host);
        bfr_filter.close();
        return false;
      } else if (HTTP_Proxy.phishing && line_filter.contains(this.host + " ")
          && line_filter.contains("phishing")) {// ����������վ������Ŀ������Ϊ��������������ͷ�����滻
        String old_host = this.host;
        this.host = line_filter.split(" ")[1];
        this.port = 80;
        this.URL = this.URL.replace(old_host, this.host);
        request_gram = request_gram.replace(old_host, this.host);// ���������е�ͷ��URL�滻Ϊ������վ��URL
        System.out.println("��վ����:\t" + this.host);
        bfr_filter.close();
        return true;
      }
    }
    bfr_filter.close();
    return true;
  }

  /**
   * to:�ж��Ƿ��л����ļ�.
   */
  public void cache() throws IOException {
    if (!new File("src/file/" + this.host).exists()) {
      new File("src/file/" + this.host).mkdir();
    }
    File cache_file =
        new File("src/file/" + this.host + "/" + this.URL.hashCode() + ".txt");
    PrintWriter proxy_out = new PrintWriter(server_socket.getOutputStream());// ����������͵���
    InputStream proxy_server_in = server_socket.getInputStream();// ��������ͻ��˷�����Ӧ����
    OutputStream proxy_client_out = client_socket.getOutputStream();// ��ͻ��˷��͵���
    if (!cache_file.exists()) {// ����Ӧ�Ļ����ļ������ڣ��򴴽����ļ����ڼ�¼�·��ص���Ӧ����
      System.out.println("�����ļ������ڣ���ת�������ļ���:" + this.URL.hashCode());
      FileOutputStream cache_file_out = new FileOutputStream(cache_file);// д�����ļ�����
      proxy_out.write(request_gram); // �������ת��ԭ����
      proxy_out.flush();
      while (true) {
        try {
          server_socket.setSoTimeout(this.socket_time_out);// ���ó�ʱʱ��������������״̬
          int b = proxy_server_in.read();// �ֽ�����ȡ��Ӧ����
          if (b == -1) {
            break;
          } else {
            cache_file_out.write(b);// д�뵽�����ļ���
            proxy_client_out.write(b);// д�뵽��ͻ��˷�����Ӧ����
            server_socket.setSoTimeout(0);
          }
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      System.out.println("��Ӧ������Դ:��������\t�½�����:��\t�ļ���:" + this.URL.hashCode() + ".txt");
      cache_file_out.close();
    } else {// �ļ��Ѿ����ڣ�����Ҫ�жϣ�������DATA���Ѹ��£��򷵻أ�����ֱ�ӽ�������Ϊ��Ӧ
      DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH);
      df.setTimeZone(TimeZone.getTimeZone("GMT"));
      this.request_gram = this.request_gram.replace("\r\n\r\n",
          "\r\nIf-Modified-Since: " + df.format(cache_file.lastModified()) + "\r\n\r\n");
      proxy_out.write(request_gram);// ���͹�����µ������ģ�������ļ�������޸�ʱ��
      proxy_out.flush();
      // ���շ�����������
      List<Byte> out_bytes = new ArrayList<>();
      while (true) {
        try {
          server_socket.setSoTimeout(this.socket_time_out);// ���ó�ʱʱ����������������
          int b = proxy_server_in.read();// ��ȡ��Ӧ����
          if (b == -1) {
            break;
          } else {
            out_bytes.add((byte) (b));
            server_socket.setSoTimeout(0);
          }
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      this.respose_byte = new byte[out_bytes.size()];
      int count = 0;// ���ڹ�����Ӧ�ֽڱ���
      for (Byte byte1 : out_bytes) {
        this.respose_byte[count++] = byte1;
      }
      this.respose_gram = new String(respose_byte, 0, count);// �����ı���Ӧ����
      if (this.respose_gram.split("\r\n")[0].contains("304")) {// ��Ӧ����ͷ��304���򻺴����
        System.out.println("����������: " + (++HTTP_Proxy.cache_hit) + "\t����: " + this.URL
            + "\t�ļ���: " + this.URL.hashCode());
        FileInputStream cache_file_read = new FileInputStream(cache_file);
        int b;// ֱ�ӽ����汨�ķ��͸��ͻ���
        while ((b = cache_file_read.read()) != -1) {
          proxy_client_out.write(b);// д��ͻ��˵���
        }
        cache_file_read.close();
        System.out.println("��Ӧ������Դ:�����ļ�\t���»���:��\t�ļ���:" + this.URL.hashCode() + ".txt");
      } else if (this.respose_gram.split("\r\n")[0].contains("200")) {// ��Ӧ����ͷ��200�����»���
        System.out.println("�����ļ����ڣ�������£��ļ���:" + this.URL.hashCode());
        FileOutputStream cache_file_out = new FileOutputStream(cache_file);// д�����ļ�����
        proxy_client_out.write(this.respose_byte);// ���ӷ�������ȡ����ת�����ͻ���
        cache_file_out.write(this.respose_byte);// ���±��ػ���
        cache_file_out.close();
        System.out.println("��Ӧ������Դ:��������\t���»���:��\t�ļ���:" + this.URL.hashCode() + ".txt");
      }
    }
  }

  /**
   * to:���̵߳�һ���̵߳�run�����������������ʵ�ִ�������������й���:ת�������ˡ�����������.
   */
  @Override public void run() {
    try {
      BufferedReader bfReader =
          new BufferedReader(new InputStreamReader(client_socket.getInputStream()));// ���ڶ�ȡ�ͻ��˷�����������
      String proxy_line = bfReader.readLine();
      if (proxy_line == null) {
        return;
      }
      this.parse_request(proxy_line);// ����ͷ���У������ö��������ֵ
      while (proxy_line != null) {
        try {
          request_gram += proxy_line + "\r\n";// ��ȡ�����ĵ���Ϣ
          client_socket.setSoTimeout(this.socket_time_out);// ���ó�ʱʱ�䣬����������������״̬
          proxy_line = bfReader.readLine();
          client_socket.setSoTimeout(0);
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      if (!this.filter_and_phishing()) {// ��վ���ˣ��û����ˣ�����
        return;
      }
      server_socket = new Socket(this.host, this.port);// �����������ͨ�ŵ��׽���
      this.cache();// �ж��������Ƿ�����ɻ����ļ���������������Ӧ�Ĳ���
      server_socket.close();
      client_socket.close();
    } catch (IOException e) {
      System.out.println("\n" + e.getMessage());
    }
  }

  /**
   * in order to:��ȡĿ������URL�Լ��������ͣ���ʼ��host��port(�������)
   */
  public void parse_request(String head_line) {
    this.URL = head_line.split("[ ]")[1];// ��ȡ�����Ŀ��URL
    int index = -1;
    this.host = this.URL;// �������ڻ�ȡ�����������
    if ((index = this.host.indexOf("http://")) != -1) {// ȥ��URL�е�http://
      this.host = this.host.substring(index + 7);
    }
    if ((index = this.host.indexOf("https://")) != -1) {// ȥ��URL�е�https://
      this.host = this.host.substring(index + 8);
    }
    if ((index = this.host.indexOf("/")) != -1) {// ȥ��URL�е�/
      this.host = this.host.substring(0, index);
    }
    if ((index = this.host.indexOf(":")) != -1) {// ȥ��URL�е�:
      this.port = Integer.valueOf(this.host.substring(index + 1));
      this.host = this.host.substring(0, index);
    }
  }

}
