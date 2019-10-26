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
import java.util.ArrayList;
import java.util.List;

public class CommunicateThread extends Thread {

  Socket client_socket;// ��ͻ���ͨ�ŵĴ�����������׽���
  Socket server_socket;// ���������ͨ�ŵĴ�����������׽���
  String request_gram = "";// �������Կͻ��˵�������
  String respose_gram = "";// �������Է���������Ӧ����
  byte[] respose_byte;
  int port = 80;
  String method;
  String URL;
  String host;

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
        new BufferedReader(new FileReader(HTTP_Proxy.filter_file));
    String line_filter = "";
    while ((line_filter = bfr_filter.readLine()) != null) {
      if (HTTP_Proxy.user_filter
          && line_filter.contains(client_socket.getInetAddress().getHostAddress())
          && line_filter.contains("user_filter")) {
        System.out.println(
            "�㲻�ܷ��ʸ���վ����Ϊ���Ǳ������û�:" + client_socket.getInetAddress().getHostAddress());
        bfr_filter.close();
        return false;
      } else if (HTTP_Proxy.web_filter && line_filter.contains(this.host)
          && line_filter.contains("web_filter")) {
        System.out.println("�㲻�ܷ��ʸ���վ����ΪĿ����վ������:" + this.host);
        bfr_filter.close();
        return false;
      } else if (HTTP_Proxy.phishing && line_filter.contains(this.host + " ")
          && line_filter.contains("phishing")) {
        this.host = line_filter.split(" ")[1];
        String old_URL = this.URL;
        this.URL = "http://" + this.host + "/";
        this.port = 80;
        request_gram = request_gram.replace(old_URL, this.URL);
        System.out.println("�㲻�ܷ���Ŀ����վ����Ϊ����վ�ѱ�������:" + this.host);
        bfr_filter.close();
        return true;
      }
    }
    bfr_filter.close();
    return true;
  }

  public void in_cache_new() throws IOException {
    if (!new File("src/file/" + this.host).exists()) {
      new File("src/file/" + this.host).mkdir();
    }
    File cache_file =
        new File("src/file/" + this.host + "/" + this.URL.hashCode() + ".dat");
    if (!cache_file.exists()) {
      cache_file.createNewFile();
      // �������ת��ԭ����
      PrintWriter proxy_out = new PrintWriter(server_socket.getOutputStream());
      proxy_out.write(request_gram);
      proxy_out.flush();
      InputStream proxy_server_in = server_socket.getInputStream();
      OutputStream proxy_client_out = client_socket.getOutputStream();
      List<Byte> out_bytes = new ArrayList<>();
      while (true) {
        try {
          server_socket.setSoTimeout(500);
          int b = proxy_server_in.read();
          if (b == -1) {
            break;
          } else {
            out_bytes.add((byte) (b));
            proxy_client_out.write(b);
            server_socket.setSoTimeout(0);
          }
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      this.respose_byte = new byte[out_bytes.size()];
      int count = 0;
      for (Byte byte1 : out_bytes) {
        this.respose_byte[count++] = byte1;
      }
      FileOutputStream cache_file_out = new FileOutputStream(cache_file);
      cache_file_out.write(this.respose_byte);
      cache_file_out.close();
    } else {// �ļ��Ѿ����ڣ�����Ҫ�жϣ�������DATA���Ѹ��£��򷵻أ�����ֱ�ӽ�������Ϊ��Ӧ
      BufferedReader cache_reader =
          new BufferedReader(new InputStreamReader(new FileInputStream(cache_file)));
      String line_cache = "";
      while ((line_cache = cache_reader.readLine()) != null) {
        this.respose_gram += line_cache + "\r\n";
        if (line_cache.startsWith("Date:")) {
          this.request_gram = this.request_gram.replace("\r\n\r\n",
              "\r\n" + "If-Modified-Since: " + line_cache.substring(6) + "\r\n\r\n");
        }
      }
      // ������������µ��޸�������
      PrintWriter proxy_out = new PrintWriter(server_socket.getOutputStream());
      proxy_out.write(request_gram);
      proxy_out.flush();
      cache_reader.close();
      // ���շ�����������
      InputStream proxy_server_in = server_socket.getInputStream();
      List<Byte> out_bytes = new ArrayList<>();
      while (true) {
        try {
          server_socket.setSoTimeout(500);
          int b = proxy_server_in.read();
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
      int count = 0;
      for (Byte byte1 : out_bytes) {
        this.respose_byte[count++] = byte1;
      }
      this.respose_gram = new String(respose_byte, 0, count);
      if (this.respose_gram.split("\r\n")[0].contains("304")) {
        // System.out.println("����������δ���£�����ֱ�����󻺴�\n�����������ڻ�����\n" + this.request_gram);
        System.out.println("����������: " + ++HTTP_Proxy.cache_hit + "\t����: " + this.URL);
        // ֱ�ӽ����汨�ķ��͸��ͻ���
        FileInputStream cache_file_read = new FileInputStream(cache_file);
        OutputStream proxy_client_out = client_socket.getOutputStream();
        int b;
        while ((b = cache_file_read.read()) != -1) {
          proxy_client_out.write(b);
        }
        cache_file_read.close();
      } else if (this.respose_gram.split("\r\n")[0].contains("200")) {
        // ���ӷ�������ȡ����ת�����ͻ��ˣ������»���
        OutputStream proxy_client_out = client_socket.getOutputStream();
        proxy_client_out.write(this.respose_byte);
      }
    }
  }

  @Override public void run() {
    try {
      BufferedReader bfReader =
          new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
      String proxy_line = bfReader.readLine();
      this.parse_request(proxy_line);
      while (proxy_line != null) {
        try {
          if (!proxy_line.contains("Cache-Control")) {
            request_gram += proxy_line + "\r\n";
          }
          client_socket.setSoTimeout(500);
          proxy_line = bfReader.readLine();
          client_socket.setSoTimeout(0);
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      if (!this.filter_and_phishing()) {// ��վ���ˣ��û����ˣ�����
        return;
      }
      server_socket = new Socket(this.host, this.port);
      this.in_cache_new();
      server_socket.close();
      client_socket.close();
    } catch (IOException e) {
      System.out.println("\n" + e.getMessage() + "\n");
    }
  }

  /**
   * in order to:��ȡĿ������URL�Լ��������ͣ���ʼ��host��port(�������)
   */
  public void parse_request(String head_line) {
    this.method = head_line.split("[ ]")[0];
    this.URL = head_line.split("[ ]")[1];
    int index = -1;
    this.host = this.URL;
    if ((index = this.host.indexOf("http://")) != -1) {
      this.host = this.host.substring(index + 7);
    }
    if ((index = this.host.indexOf("/")) != -1) {
      this.host = this.host.substring(0, index);
    }
    if ((index = this.host.indexOf(":")) != -1) {
      this.port = Integer.valueOf(this.host.substring(index + 1));
      this.host = this.host.substring(0, index);
    }
  }

}
