import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../services/api';
import type { DemoState, Match } from '../types';

export function MatchingPage() {
  const { id } = useParams();
  const [state, setState] = useState<DemoState>();
  const [match, setMatch] = useState<Match>();
  const [error, setError] = useState('');

  useEffect(() => {
    async function load() {
      try {
        const current = await api.demoState();
        setState(current);
        const matches = current.jobId ? await api.getMatches(current.jobId) : [];
        setMatch(matches.find((item) => item.id === id));
      } catch (reason) {
        setError(reason instanceof Error ? reason.message : '매칭 상태를 불러오지 못했습니다.');
      }
    }
    void load();
  }, [id]);

  return (
    <main className="container">
      <div className="page-heading"><Link to="/">← 처음으로</Link></div>
      <section className="card">
        <h1>매칭 진행 상태</h1>
        {error && <p className="alert error" role="alert">{error}</p>}
        {!match ? (
          <p>이 브라우저의 데모 세션에서 매칭 정보를 찾을 수 없습니다.</p>
        ) : (
          <>
            <p className={match.status === 'CONFIRMED' ? 'status-confirmed' : 'alert warning'}>
              {match.status === 'CONFIRMED' ? '양쪽 동의 완료 · 매칭 확정' : '매칭 확정 전 · 양쪽 승인이 필요합니다.'}
            </p>
            <dl className="details-grid">
              <dt>작업</dt><dd>{String(state?.job?.workType ?? '-')}</dd>
              <dt>구직자</dt><dd>{match.nickname}</dd>
              <dt>추천 점수</dt><dd>{match.totalScore}점</dd>
              <dt>농가 승인</dt><dd>{match.farmerApproved ? '완료' : '대기'}</dd>
              <dt>구직자 수락</dt><dd>{match.workerAccepted ? '완료' : '대기'}</dd>
            </dl>
            <h2>추천 근거</h2>
            <ul>{match.scoreReasons.map((reason) => <li key={reason}>{reason}</li>)}</ul>
            <p className="muted">데모 상태이며 실제 고용 계약이나 연락을 만들지 않습니다.</p>
          </>
        )}
      </section>
    </main>
  );
}
