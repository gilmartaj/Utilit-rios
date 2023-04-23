import java.net.*;
import java.io.*;

public class Dow2k2{
    public static int MAXIMO_TENTATIVAS = 100;
    
    public static void main(String[] args)
    {
        int baixado = 0;
        int tentativas = 0;
        try{
            final String URL_ARQUIVO = args[0];
            URL url = new URL(URL_ARQUIVO);
            File arquivoASerBaixado = new File(URL_ARQUIVO);
            
            HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
            //con.setRequestMethod("HEAD");
            long tamanhoTotalArquivo = conexao.getContentLengthLong();
            //conexao = url.openConnection();
            //while(conexao.getHeaderField("Content-Disposition") == null)Thread.sleep(30);
            File arquivoSalvo;
            String nomeArquivo;
            if(args.length > 1)
                arquivoSalvo = new File(args[1]);
            else
            {
                nomeArquivo = conexao.getHeaderField("Content-Disposition");
                if(nomeArquivo != null && nomeArquivo.contains("filename=\"")){
                nomeArquivo = new String(nomeArquivo.getBytes("ISO-8859-1"));
                    arquivoSalvo = new File(nomeArquivo.substring(nomeArquivo.indexOf("filename=\"")+10, nomeArquivo.length() - 1));
                }else
                    arquivoSalvo = new File(arquivoASerBaixado.getName());
            }
            
            while(arquivoSalvo.length() < tamanhoTotalArquivo && tentativas <= MAXIMO_TENTATIVAS){
                conexao.disconnect();
                conexao = (HttpURLConnection) url.openConnection();
                if(arquivoSalvo.exists())
                    conexao.setRequestProperty("Range", "bytes=" + arquivoSalvo.length() + "-" + tamanhoTotalArquivo);
                
                if (tentativas > 0)
/* 43 */           System.out.print(String.format("%c[9A\r%c[0J", new Object[] { Integer.valueOf(27), Integer.valueOf(27) })); 
                
                //while(conexao.getContentLength() == 0)Thread.sleep(5000);
                System.out.println("\n Tamanho total: " + tamanhoTotalArquivo + " Bytes = " + tamanhoTotalArquivo/1048576 + " MB");
                System.out.println("\n Por baixar: " + conexao.getContentLength() + 
                                       " Bytes = " + conexao.getContentLength()/1048576 + " MB");
                System.out.println("\n" + arquivoSalvo.getName());
                
                try (BufferedInputStream in = new BufferedInputStream(conexao.getInputStream()))
                {
                    OutputStream os = new FileOutputStream(arquivoSalvo.getName(), true);
                    byte dataBuffer[] = new byte[1048576];
                    int bytesRead;
                    while ((bytesRead = in.read(dataBuffer, 0, 1048576)) != -1) 
                    {
                        os.write(dataBuffer, 0, bytesRead);
                        //if(baixado % 500 == 0)
                        baixado+=bytesRead;
                        System.out.print("\rBaixado = " + (baixado/1048576) + " MB");
                        //try {
/* 62 */                 //Thread.sleep(1L);
/* 63 */               //} catch (Exception exception) {}
                        //baixado++;
                    }
                    
                } catch (IOException e) 
                {
                    e.printStackTrace();
                }
                if(arquivoSalvo.length() < tamanhoTotalArquivo)
                {
                    System.out.println("\nOcorreu um erro. Tentando continuar...");
                    Thread.sleep(1000);
                }
                tentativas++;
            }
            
            if(tentativas <= MAXIMO_TENTATIVAS)
                {
                System.out.println("\nDownload concluído com " + tentativas + " tentativas.");
                System.exit(0);
                }
            else{
                System.out.println("O Download não pode ser concluído.");
                System.exit(1);
                }
        }catch(Exception e)
        {
            e.printStackTrace();
        }
        System.exit(2);
    }
}
