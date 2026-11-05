import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <main className="container home-page">
      <section className="card home-hero">
        <div className="home-hero-copy">
          <p className="eyebrow">청도 농촌 일손 연결 데모</p>
          <h1>필요한 일손과<br /><span>좋은 일자리</span>를 잇다</h1>
          <p className="home-lead">
            농가의 일손 요청부터 구직자의 수락까지,
            서로의 조건을 확인하며 차근차근 연결해요.
          </p>
          <div className="home-trust">
            <span>로그인 없이 시작</span>
            <span>양쪽 동의로 매칭</span>
          </div>
        </div>
        <div className="home-art" aria-hidden="true">
          <span className="home-art-leaf" />
        </div>
      </section>

      <section className="home-roles" aria-labelledby="role-heading">
        <h2 id="role-heading" className="home-section-heading">어떤 역할로 시작할까요?</h2>
        <div className="grid two-col">
          <Link to="/farmer-demo" className="role-card">
            <span className="role-icon" aria-hidden="true">농가</span>
            <span className="role-copy">
              <h3>일손이 필요한 농가예요</h3>
              <p>작업 조건을 적고, 잘 맞는 후보를 살펴봐요.</p>
            </span>
            <span className="role-arrow" aria-hidden="true">→</span>
          </Link>
          <Link to="/worker-demo" className="role-card">
            <span className="role-icon worker" aria-hidden="true">구직</span>
            <span className="role-copy">
              <h3>일자리를 찾는 구직자예요</h3>
              <p>작업 내용을 확인하고 참여 여부를 정해요.</p>
            </span>
            <span className="role-arrow" aria-hidden="true">→</span>
          </Link>
        </div>
        <p className="home-footnote">데모 승인과 수락은 실제 고용 계약이나 연락을 만들지 않습니다.</p>
      </section>
    </main>
  );
}
