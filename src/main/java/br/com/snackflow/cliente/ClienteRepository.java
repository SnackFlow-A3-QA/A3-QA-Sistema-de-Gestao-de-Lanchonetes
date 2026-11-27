package br.com.snackflow.cliente;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acesso a clientes. As buscas usam o caractere de escape {@code !} (não a barra invertida, que o
 * MySQL também trata como escape de literal). Quem chama deve escapar {@code %}, {@code _} e
 * {@code !} do texto digitado (veja {@code ClienteService#escaparLike}).
 */
public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    boolean existsByCpf(String cpf);

    /** Duplicidade na edição: ignora o próprio cliente. */
    boolean existsByCpfAndIdNot(String cpf, Integer id);

    @Query("select c from Cliente c where c.ativo = true "
            + "and lower(c.nome) like lower(:padrao) escape '!' order by c.nome, c.id")
    List<Cliente> buscarPorNome(@Param("padrao") String padrao, Pageable paginacao);

    @Query("select c from Cliente c where c.ativo = true "
            + "and c.cpf like :padrao escape '!' order by c.nome, c.id")
    List<Cliente> buscarPorCpf(@Param("padrao") String padrao, Pageable paginacao);
}
