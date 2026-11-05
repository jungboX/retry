export function WorkerDemoPage() {
  return (
    <div className="container">
      <div className="card">
        <h1>구직자 데모</h1>
        <div className="grid two-col">
          <div>
            <h3>작업 조건</h3>
            <ul>
              <li>지역: 청도군 매전면 반경 20km</li>
              <li>가능일: 2026-11-09 ~ 2026-11-11</li>
              <li>경험: 2년 이상</li>
              <li>작업 유형: 과수 작업, 수확 보조</li>
            </ul>
          </div>
          <div>
            <h3>추천 매칭</h3>
            <div className="badge">점수 87</div>
            <p>안전 장비 확인 가능, 근거리 출근 가능, 농작업 경험이 적합합니다.</p>
            <div className="row">
              <button type="button">수락하기</button>
              <button type="button" className="secondary">다음 후보</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
