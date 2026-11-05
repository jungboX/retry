import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <div className="container">
      <div className="card">
        <h1>청도 일손이음</h1>
        <p>청도 농가의 일손 요청을 정리하고, 추천 근거를 확인한 뒤 농가와 구직자가 함께 승인하는 데모입니다.</p>
        <p className="muted">로그인 없이 이용할 수 있습니다. 데모 승인은 실제 고용 계약이나 연락을 만들지 않습니다.</p>
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
