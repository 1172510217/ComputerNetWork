package lab1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class CommunicateThread extends Thread {

  Socket client_socket;// ��ͻ���ͨ�ŵĴ�����������׽���
  Socket server_socket;// ���������ͨ�ŵĴ�����������׽���
  String request_gram = "";// �������Կͻ��˵�������
  int port = 80;
  String method;
  String URL;
  String host;

  public CommunicateThread(Socket client_socket) {
    this.client_socket = client_socket;
  }

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
      } else if (HTTP_Proxy.phishing && line_filter.contains(this.host)
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

  @Override public void run() {
    try {
      BufferedReader bfReader =
          new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
      String proxy_in_line = bfReader.readLine();
      this.parse_request(proxy_in_line);
      while (proxy_in_line != null) {
        try {
          request_gram += proxy_in_line + "\r\n";
          client_socket.setSoTimeout(500);
          proxy_in_line = bfReader.readLine();
          client_socket.setSoTimeout(0);
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      if (!this.filter_and_phishing()) {// ��վ���ˣ��û����ˣ�����
        return;
      }
      server_socket = new Socket(this.host, this.port);
      PrintWriter proxy_out = new PrintWriter(server_socket.getOutputStream());
      System.out.print(request_gram);
      proxy_out.write(request_gram);
      proxy_out.flush();
      System.out.println(
          "Ŀ������:" + this.host + "\nĿ�Ķ˿ں�:" + this.port + "\n��������:" + this.method);
      System.out.println(this.host + ":�������ת�����ݽ���");
      // �����Ѿ�ת�����������ˣ����濪ʼ�ӷ�����ȡ���ݲ�����ת�����ͻ���
      InputStream proxy_server_in = server_socket.getInputStream();
      OutputStream proxy_client_out = client_socket.getOutputStream();
      while (true) {
        try {
          server_socket.setSoTimeout(500);
          int b = proxy_server_in.read();
          if (b == -1) {
            break;
          } else {
            proxy_client_out.write(b);
            server_socket.setSoTimeout(0);
          }
        } catch (SocketTimeoutException e) {
          break;
        }
      }
      System.out.println(this.host + ":�ͻ��˽����������");
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
