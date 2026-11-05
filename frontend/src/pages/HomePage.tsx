import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <div className="container">
      <div className="card">
        <h1>청도 일손이음</h1>
        <p>농가 요청을 구조화하고, 후보를 추천하고, 승인으로 연결하는 데모입니다.</p>
        <div className="grid two-col" style={{ marginTop: 24 }}>
          <Link to="/farmer-demo" style={{ textDecoration: 'none', color: 'inherit' }}>
            <div className="card">
              <h2>농가 데모</h2>
              <p>작업 요구를 입력하고 후보를 검토합니다.</p>
            </div>
          </Link>
          <Link to="/worker-demo" style={{ textDecoration: 'none', color: 'inherit' }}>
            <div className="card">
              <h2>구직자 데모</h2>
              <p>가능 시간과 근무 조건을 확인하여 수락합니다.</p>
            </div>
          </Link>
        </div>
      </div>
    </div>
  );
}
