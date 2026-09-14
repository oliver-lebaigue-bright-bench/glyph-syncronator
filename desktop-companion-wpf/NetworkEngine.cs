using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace GlyphixDesktopCompanion
{
    public class NetworkEngine
    {
        private const int UDP_PORT = 12347;
        private const int DISCOVERY_PORT = 12348;

        private UdpClient? _listener;
        private UdpClient? _discoveryResponder;
        private CancellationTokenSource? _cts;

        public Action<byte[], IPEndPoint>? OnAudioDataReceived;
        public Action<string>? OnLog;

        public string LocalIP => GetLocalIPAddress();

        private string GetLocalIPAddress()
        {
            try
            {
                using (Socket socket = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, 0))
                {
                    socket.Connect("8.8.8.8", 65530);
                    IPEndPoint? endPoint = socket.LocalEndPoint as IPEndPoint;
                    return endPoint?.Address.ToString() ?? "127.0.0.1";
                }
            }
            catch
            {
                return "127.0.0.1";
            }
        }

        public void StartListener()
        {
            StopListener();
            _cts = new CancellationTokenSource();
            _listener = new UdpClient(UDP_PORT);

            Task.Run(async () =>
            {
                while (!_cts.Token.IsCancellationRequested)
                {
                    try
                    {
                        var result = await _listener.ReceiveAsync(_cts.Token);
                        OnAudioDataReceived?.Invoke(result.Buffer, result.RemoteEndPoint);
                    }
                    catch (OperationCanceledException) { break; }
                    catch (Exception ex)
                    {
                        OnLog?.Invoke($"Listener Error: {ex.Message}");
                    }
                }
            }, _cts.Token);
        }

        public void StopListener()
        {
            _cts?.Cancel();
            _listener?.Close();
            _listener = null;
        }

        public async Task StreamAudioAsync(byte[] data, string phoneIp, int port = UDP_PORT)
        {
            using var client = new UdpClient();
            await client.SendAsync(data, data.Length, phoneIp, port);
        }

        public void StartDiscoveryResponder()
        {
            _discoveryResponder = new UdpClient(DISCOVERY_PORT);
            _discoveryResponder.EnableBroadcast = true;

            Task.Run(async () =>
            {
                while (true)
                {
                    try
                    {
                        var result = await _discoveryResponder.ReceiveAsync();
                        string msg = Encoding.UTF8.GetString(result.Buffer);
                        if (msg.Contains("GLYPHIX") || msg.Contains("DISCOVERY"))
                        {
                            byte[] resp = Encoding.UTF8.GetBytes($"GLYPHIX_PC_DISCOVERY_RESPONSE:{LocalIP}");
                            await _discoveryResponder.SendAsync(resp, resp.Length, result.RemoteEndPoint);
                            OnLog?.Invoke($"Discovery: Responded to {result.RemoteEndPoint.Address}");
                        }
                    }
                    catch { }
                }
            });
        }

        public async Task<string?> DiscoverPhoneAsync(CancellationToken ct)
        {
            using var client = new UdpClient();
            client.EnableBroadcast = true;
            client.Client.ReceiveTimeout = 500;

            byte[] msg = Encoding.UTF8.GetBytes("GLYPHIX_DISCOVERY_REQUEST");
            string[] broadcasts = GetBroadcastAddresses();

            for (int i = 0; i < 10; i++)
            {
                if (ct.IsCancellationRequested) return null;

                foreach (var b in broadcasts)
                {
                    await client.SendAsync(msg, msg.Length, new IPEndPoint(IPAddress.Parse(b), DISCOVERY_PORT));
                }

                try
                {
                    var result = await client.ReceiveAsync(ct);
                    string resp = Encoding.UTF8.GetString(result.Buffer);
                    if (resp.Contains("GLYPHIX"))
                    {
                        return result.RemoteEndPoint.Address.ToString();
                    }
                }
                catch { }
                await Task.Delay(200, ct);
            }
            return null;
        }

        private string[] GetBroadcastAddresses()
        {
            var ips = new System.Collections.Generic.List<string> { "255.255.255.255" };
            string local = LocalIP;
            if (local != "127.0.0.1")
            {
                var parts = local.Split('.');
                if (parts.Length == 4)
                {
                    ips.Add($"{parts[0]}.{parts[1]}.{parts[2]}.255");
                }
            }
            return ips.ToArray();
        }
    }
}
