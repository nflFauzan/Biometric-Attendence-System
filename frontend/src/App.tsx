import { useEffect, useState } from 'react'

type ServerStatus = 'memeriksa' | 'terhubung' | 'tidak terhubung'

function App() {
  const [status, setStatus] = useState<ServerStatus>('memeriksa')

  useEffect(() => {
    fetch('/actuator/health')
      .then((res) => setStatus(res.ok ? 'terhubung' : 'tidak terhubung'))
      .catch(() => setStatus('tidak terhubung'))
  }, [])

  return (
    <main style={{ padding: 24 }}>
      <h1>Biometric Attendance System</h1>
      <p>Status server: {status}</p>
    </main>
  )
}

export default App
