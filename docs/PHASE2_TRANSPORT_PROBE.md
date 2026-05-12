# Phase 2 Transport Probe

This build is intentionally a transport validator before full SSH auth/proxy implementation.

Expected success in `/data/adb/sshcustom/run/core.log`:

```text
transport attempt #1 starting
dial tcp www.bc.game:80
sending payload timing=after_proxy_socket_before_ssh bytes=...
transport verified: banner="SSH-2.0-dropbear_2020.81" http_statuses=[101 200]
```

Expected API fields:

```json
{
  "phase": "phase2-transport-probe",
  "state": "TRANSPORT_READY_PHASE2",
  "transport_ready": true,
  "ssh_authenticated": false,
  "remote_banner": "SSH-2.0-dropbear_2020.81"
}
```

`ssh_authenticated` stays false until Phase 3, where SSH auth and channel forwarding are added.
